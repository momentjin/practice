package dev.momentjin.batchpracitce.practice.common.settlement

// DB 엔티티가 아니라 Processor가 계산해서 Writer에 넘기는 결과다.
data class SettlementPreview(
    val orderId: Long,
    val orderNumber: String,
    val orderAmount: Long,
    val feeAmount: Long,
    val payoutAmount: Long,
)
