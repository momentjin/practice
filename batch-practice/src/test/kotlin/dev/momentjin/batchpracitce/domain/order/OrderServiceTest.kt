package dev.momentjin.batchpracitce.domain.order

import dev.momentjin.batchpracitce.domain.merchant.MerchantService
import dev.momentjin.batchpracitce.domain.order.CreateOrderCommand
import dev.momentjin.batchpracitce.domain.order.OrderService
import dev.momentjin.batchpracitce.domain.order.*
import dev.momentjin.batchpracitce.domain.ledger.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.test.*

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceTest @Autowired constructor(
    private val merchants: MerchantService,
    private val orders: OrderService,
    private val ledgers: PaymentLedgerRepository,
) {
    @Test
    fun `결제와 취소는 주문 상태를 변경하고 각각의 원장을 남긴다`() {
        val merchant = merchants.register("merchant-1", "테스트 가맹점")
        val at = LocalDateTime.of(2026, 9, 1, 12, 0)
        val order = orders.create(CreateOrderCommand("order-1", merchant.id, 10_000, PaymentChannel.APP, at))
        assertEquals(OrderStatus.CREATED, order.status)
        assertEquals(OrderStatus.PAID, orders.pay(order.id, at.plusMinutes(1)).status)
        assertEquals(OrderStatus.CANCELLED, orders.cancel(order.id, at.plusHours(1)).status)
        val entries = ledgers.findAllByOrderIdOrderByIdAsc(order.id)
        assertEquals(listOf(LedgerEntryType.PAYMENT, LedgerEntryType.CANCELLATION), entries.map { it.entryType })
        assertEquals(listOf(10_000L, 10_000L), entries.map { it.amount })
    }

    @Test
    fun `생성된 주문은 결제 전 취소할 수 없다`() {
        val merchant = merchants.register("merchant-2", "테스트 가맹점")
        val at = LocalDateTime.of(2026, 9, 1, 12, 0)
        val order = orders.create(CreateOrderCommand("order-2", merchant.id, 10_000, PaymentChannel.WEB, at))
        assertFailsWith<IllegalStateException> { orders.cancel(order.id, at.plusMinutes(1)) }
        assertTrue(ledgers.findAllByOrderIdOrderByIdAsc(order.id).isEmpty())
    }
}
