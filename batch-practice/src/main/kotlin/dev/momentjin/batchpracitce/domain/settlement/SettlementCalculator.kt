package dev.momentjin.batchpracitce.domain.settlement

import java.math.BigDecimal
import java.math.RoundingMode

/** 금액 정책은 Spring Batch와 독립된 업무 규칙이다. */
object SettlementCalculator {
    fun calculate(orderAmount: Long): SettlementAmounts {
        require(orderAmount > 0)
        val fee = BigDecimal.valueOf(orderAmount).multiply(BigDecimal("0.03"))
            .setScale(0, RoundingMode.DOWN).longValueExact()
        return SettlementAmounts(orderAmount, fee, orderAmount - fee)
    }
}

data class SettlementAmounts(val orderAmount: Long, val feeAmount: Long, val payoutAmount: Long)
