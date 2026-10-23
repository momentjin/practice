package dev.momentjin.batchpracitce.practice.common.settlement

import dev.momentjin.batchpracitce.domain.merchant.Merchant
import dev.momentjin.batchpracitce.domain.order.PaymentChannel
import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class SettlementPreviewProcessorTest {
    private val processor = SettlementPreviewProcessor()

    @Test
    fun `3퍼센트 수수료의 원 미만을 버리고 지급액을 계산한다`() {
        val preview = processor.process(sampleOrder(10_099))
        assertEquals(302L, preview.feeAmount)
        assertEquals(9_797L, preview.payoutAmount)
        assertEquals(preview.orderAmount, preview.feeAmount + preview.payoutAmount)
    }

    @Test
    fun `소액 주문은 수수료가 0원이 될 수 있다`() {
        val preview = processor.process(sampleOrder(1))
        assertEquals(0L, preview.feeAmount)
        assertEquals(1L, preview.payoutAmount)
    }

    // 운영 엔티티의 ID setter를 열지 않고 저장된 주문을 표현하기 위한 테스트 객체다.
    private fun sampleOrder(amount: Long): PurchaseOrder = object : PurchaseOrder(
        "test-order", Merchant("test-merchant", "테스트 가맹점"), amount,
        PaymentChannel.APP, LocalDateTime.of(2026, 9, 1, 0, 0),
    ) {
        init { id = 1L }
    }
}
