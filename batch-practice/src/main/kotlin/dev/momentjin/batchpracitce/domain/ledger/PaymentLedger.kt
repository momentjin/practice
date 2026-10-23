package dev.momentjin.batchpracitce.domain.ledger

import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment_ledgers",
    uniqueConstraints = [UniqueConstraint(name = "uk_ledger_order_type", columnNames = ["order_id", "entry_type"])],
)
class PaymentLedger private constructor(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    val order: PurchaseOrder,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "entry_type", nullable = false, length = 20)
    val entryType: LedgerEntryType,
    @Column(nullable = false)
    val amount: Long,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    companion object {
        fun payment(order: PurchaseOrder) = PaymentLedger(
            order, LedgerEntryType.PAYMENT, order.amount, requireNotNull(order.paidAt),
        )

        fun cancellation(order: PurchaseOrder) = PaymentLedger(
            order, LedgerEntryType.CANCELLATION, order.amount, requireNotNull(order.cancelledAt),
        )
    }
}

enum class LedgerEntryType { PAYMENT, CANCELLATION }
