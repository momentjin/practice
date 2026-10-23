package dev.momentjin.batchpracitce.domain.order

import dev.momentjin.batchpracitce.domain.merchant.Merchant
import dev.momentjin.batchpracitce.domain.order.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.*

class PurchaseOrderTest {
    private val at = LocalDateTime.of(2026, 9, 1, 12, 0)
    private fun order(amount: Long = 10_000) = PurchaseOrder("order", Merchant("m", "가맹점"), amount, PaymentChannel.APP, at)

    @Test
    fun `0원 이하 주문을 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { order(0) }
        assertFailsWith<IllegalArgumentException> { order(-1) }
    }

    @Test
    fun `중복 결제와 중복 취소를 허용하지 않는다`() {
        val order = order()
        order.pay(at.plusMinutes(1))
        assertFailsWith<IllegalStateException> { order.pay(at.plusMinutes(2)) }
        order.cancel(at.plusHours(1))
        assertFailsWith<IllegalStateException> { order.cancel(at.plusHours(2)) }
    }

    @Test
    fun `주문과 결제보다 앞선 시각으로 전이할 수 없다`() {
        val order = order()
        assertFailsWith<IllegalArgumentException> { order.pay(at.minusSeconds(1)) }
        order.pay(at.plusMinutes(1))
        assertFailsWith<IllegalArgumentException> { order.cancel(at) }
    }
}
