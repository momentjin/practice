package dev.momentjin.batchpracitce.domain.order

import dev.momentjin.batchpracitce.domain.merchant.Merchant
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "purchase_orders")
class PurchaseOrder(
    @Column(name = "order_number", nullable = false, unique = true, length = 80)
    val orderNumber: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    val merchant: Merchant,
    @Column(name = "amount", nullable = false)
    val amount: Long,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    val channel: PaymentChannel,
    @Column(name = "ordered_at", nullable = false)
    val orderedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Version
    var version: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.CREATED
        protected set

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = null
        protected set

    @Column(name = "cancelled_at")
    var cancelledAt: LocalDateTime? = null
        protected set

    init {
        require(orderNumber.isNotBlank() && orderNumber.length <= 80) { "주문 번호는 1~80자여야 합니다." }
        require(amount > 0) { "주문 금액은 0보다 커야 합니다." }
    }

    fun pay(at: LocalDateTime) {
        check(status == OrderStatus.CREATED) { "생성된 주문만 결제할 수 있습니다." }
        require(!at.isBefore(orderedAt)) { "결제 시각은 주문 시각보다 빠를 수 없습니다." }
        status = OrderStatus.PAID
        paidAt = at
    }

    fun cancel(at: LocalDateTime) {
        check(status == OrderStatus.PAID) { "결제된 주문만 취소할 수 있습니다." }
        require(!at.isBefore(requireNotNull(paidAt))) { "취소 시각은 결제 시각보다 빠를 수 없습니다." }
        status = OrderStatus.CANCELLED
        cancelledAt = at
    }
}
