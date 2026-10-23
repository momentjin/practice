package dev.momentjin.batchpracitce.practice.common.settlement

import dev.momentjin.batchpracitce.practice.common.settlement.SettlementPreview
import dev.momentjin.batchpracitce.domain.merchant.MerchantService
import dev.momentjin.batchpracitce.domain.order.CreateOrderCommand
import dev.momentjin.batchpracitce.domain.order.OrderService
import dev.momentjin.batchpracitce.domain.order.PaymentChannel
import dev.momentjin.batchpracitce.domain.settlement.SettlementResultRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(properties = ["spring.batch.job.enabled=false"])
@ActiveProfiles("test")
class SettlementResultWriterTest @Autowired constructor(
    private val writer: SettlementResultWriter,
    private val transactions: TransactionTemplate,
    private val results: SettlementResultRepository,
    private val merchants: MerchantService,
    private val orders: OrderService,
) {
    @Test
    fun `같은 주문과 같은 금액을 새 트랜잭션으로 다시 저장해도 결과는 하나다`() {
        val preview = preview()
        transactions.executeWithoutResult { writer.write(Chunk(listOf(preview))) }
        val first = assertNotNull(results.findByOrderId(preview.orderId))
        transactions.executeWithoutResult { writer.write(Chunk(listOf(preview))) }
        val second = assertNotNull(results.findByOrderId(preview.orderId))
        assertEquals(first.id, second.id)
        assertEquals(first.createdAt, second.createdAt)
        assertEquals(1, results.findAllByOrderIdIn(listOf(preview.orderId)).size)
    }

    @Test
    fun `이미 저장된 결과와 금액이 다르면 덮어쓰지 않고 실패한다`() {
        val preview = preview()
        transactions.executeWithoutResult { writer.write(Chunk(listOf(preview))) }
        val changed = preview.copy(feeAmount = 400, payoutAmount = 9_600)
        assertFailsWith<IllegalStateException> {
            transactions.executeWithoutResult { writer.write(Chunk(listOf(changed))) }
        }
        assertEquals(300L, assertNotNull(results.findByOrderId(preview.orderId)).feeAmount)
    }

    @Test
    fun `flush 후 예외가 나면 같은 트랜잭션의 정산 결과는 전부 롤백된다`() {
        val first = preview()
        val second = preview()
        assertFailsWith<IllegalStateException> {
            transactions.executeWithoutResult {
                writer.write(Chunk(listOf(first, second)))
                error("저장 SQL 실행 후 커밋 전 실패")
            }
        }
        assertNull(results.findByOrderId(first.orderId))
        assertNull(results.findByOrderId(second.orderId))
    }

    private fun preview(): SettlementPreview {
        val key = UUID.randomUUID().toString()
        val merchant = merchants.register(key, "정산 테스트 가맹점")
        val at = LocalDateTime.of(2026, 9, 1, 12, 0)
        val order = orders.create(CreateOrderCommand(key, merchant.id, 10_000, PaymentChannel.APP, at))
        orders.pay(order.id, at.plusMinutes(1))
        return SettlementPreview(order.id, order.orderNumber, 10_000, 300, 9_700)
    }
}
