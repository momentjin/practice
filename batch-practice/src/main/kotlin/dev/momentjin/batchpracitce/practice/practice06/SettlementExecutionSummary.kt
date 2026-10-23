package dev.momentjin.batchpracitce.practice.practice06

// 실행별 집계다. 재시작 이전에 처리한 위치는 resumeFromCount로 분리한다.
data class SettlementExecutionSummary(
    val jobExecutionId: Long,
    val targetCount: Long,
    val resumeFromCount: Long,
    val insertedCount: Long,
    val existingCount: Long,
    val skippedCount: Long,
    val insertedPayoutAmount: Long,
    val status: String,
) {
    val processedThisExecution: Long get() = insertedCount + existingCount + skippedCount
    val completedCount: Long get() = resumeFromCount + processedThisExecution
    val progressPercent: Double get() = if (targetCount == 0L) 100.0 else completedCount * 100.0 / targetCount
}
