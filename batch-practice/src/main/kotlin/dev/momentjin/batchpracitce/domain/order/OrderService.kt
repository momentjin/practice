package dev.momentjin.batchpracitce.domain.order

import dev.momentjin.batchpracitce.domain.ledger.PaymentLedger
import dev.momentjin.batchpracitce.domain.ledger.PaymentLedgerRepository
import dev.momentjin.batchpracitce.domain.merchant.MerchantRepository
import dev.momentjin.batchpracitce.domain.order.*
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class OrderService(
    private val orders: OrderRepository,
    private val merchants: MerchantRepository,
    private val ledgers: PaymentLedgerRepository,
) {
    @Transactional
    fun create(command: CreateOrderCommand): OrderView {
        val merchant = merchants.findByIdOrNull(command.merchantId)
            ?: throw NoSuchElementException("가맹점을 찾을 수 없습니다: ${command.merchantId}")
        val order = PurchaseOrder(command.orderNumber, merchant, command.amount, command.channel, command.orderedAt)
        return OrderView.from(orders.save(order))
    }

    fun find(orderId: Long): OrderView = OrderView.from(getOrder(orderId))

    @Transactional
    fun pay(orderId: Long, at: LocalDateTime): OrderView {
        val order = getOrder(orderId)
        order.pay(at)
        ledgers.save(PaymentLedger.payment(order))
        return OrderView.from(order)
    }

    @Transactional
    fun cancel(orderId: Long, at: LocalDateTime): OrderView {
        val order = getOrder(orderId)
        order.cancel(at)
        ledgers.save(PaymentLedger.cancellation(order))
        return OrderView.from(order)
    }

    private fun getOrder(id: Long): PurchaseOrder = orders.findByIdOrNull(id)
        ?: throw NoSuchElementException("주문을 찾을 수 없습니다: $id")
}

data class CreateOrderCommand(
    val orderNumber: String,
    val merchantId: Long,
    val amount: Long,
    val channel: PaymentChannel,
    val orderedAt: LocalDateTime,
)

data class OrderView(
    val id: Long,
    val orderNumber: String,
    val merchantId: Long,
    val amount: Long,
    val status: OrderStatus,
    val channel: PaymentChannel,
) {
    companion object {
        fun from(order: PurchaseOrder) = OrderView(
            requireNotNull(order.id), order.orderNumber, requireNotNull(order.merchant.id),
            order.amount, order.status, order.channel,
        )
    }
}
