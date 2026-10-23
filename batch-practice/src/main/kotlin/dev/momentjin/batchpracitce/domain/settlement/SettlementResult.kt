package dev.momentjin.batchpracitce.domain.settlement

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

// 한 주문의 계산 결과를 기록한다. 이번 예제에서는 기존 결과를 수정하지 않는다.
@Entity
@Table(name = "settlement_results")
class SettlementResult(
    @Column(name = "order_id", nullable = false, unique = true)
    val orderId: Long,
    @Column(name = "order_amount", nullable = false)
    val orderAmount: Long,
    @Column(name = "fee_amount", nullable = false)
    val feeAmount: Long,
    @Column(name = "payout_amount", nullable = false)
    val payoutAmount: Long,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    init {
        require(orderId > 0) { "주문 ID는 양수여야 합니다." }
        require(orderAmount > 0) { "주문 금액은 양수여야 합니다." }
        require(feeAmount in 0..orderAmount) { "수수료는 0 이상 주문 금액 이하여야 합니다." }
        require(payoutAmount == orderAmount - feeAmount) { "지급액은 주문 금액에서 수수료를 뺀 값이어야 합니다." }
    }

    fun hasSameAmounts(orderAmount: Long, feeAmount: Long, payoutAmount: Long): Boolean =
        this.orderAmount == orderAmount && this.feeAmount == feeAmount && this.payoutAmount == payoutAmount
}
