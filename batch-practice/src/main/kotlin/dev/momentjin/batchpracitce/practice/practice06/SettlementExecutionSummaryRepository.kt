package dev.momentjin.batchpracitce.practice.practice06

import dev.momentjin.batchpracitce.practice.common.settlement.SettlementWriteResult
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

/** 집계 업데이트는 호출한 Chunk의 트랜잭션에 참여한다. */
@Repository
@Profile("practice06")
class SettlementExecutionSummaryRepository(private val jdbc: JdbcTemplate) {
    fun countTargets(orderPrefix: String, orderNumberLength: Int, maxItems: Int): Long = minOf(maxItems.toLong(), jdbc.queryForObject(
        "SELECT COUNT(*) FROM purchase_orders WHERE status = 'PAID' AND order_number LIKE ? AND CHAR_LENGTH(order_number) = ?",
        Long::class.java, "$orderPrefix%", orderNumberLength,
    )!!)

    fun initialize(executionId: Long, instanceId: Long, dataset: String, targetCount: Long, resumeFrom: Long) {
        jdbc.update(
            """
            INSERT INTO settlement_execution_summary
                (job_execution_id, job_instance_id, dataset, target_count, resume_from_count, status, started_at)
            VALUES (?, ?, ?, ?, ?, 'STARTED', ?)
            """.trimIndent(), executionId, instanceId, dataset, targetCount, resumeFrom, Timestamp.valueOf(LocalDateTime.now()),
        )
    }

    fun addWritten(executionId: Long, result: SettlementWriteResult) {
        val changed = jdbc.update(
            """
            UPDATE settlement_execution_summary
            SET inserted_count = inserted_count + ?, existing_count = existing_count + ?,
                inserted_payout_amount = inserted_payout_amount + ?
            WHERE job_execution_id = ?
            """.trimIndent(), result.insertedCount, result.existingCount, result.insertedPayoutAmount, executionId,
        )
        check(changed == 1) { "실행 집계가 초기화되지 않았습니다: $executionId" }
    }

    fun finish(executionId: Long, status: String, elapsedMillis: Long) {
        jdbc.update(
            "UPDATE settlement_execution_summary SET status = ?, ended_at = ?, elapsed_millis = ? WHERE job_execution_id = ?",
            status, Timestamp.valueOf(LocalDateTime.now()), elapsedMillis, executionId,
        )
    }

    fun find(executionId: Long): SettlementExecutionSummary = requireNotNull(jdbc.queryForObject(
        "SELECT * FROM settlement_execution_summary WHERE job_execution_id = ?",
        { rs, _ -> SettlementExecutionSummary(
            rs.getLong("job_execution_id"), rs.getLong("target_count"), rs.getLong("resume_from_count"),
            rs.getLong("inserted_count"), rs.getLong("existing_count"), rs.getLong("skipped_count"),
            rs.getLong("inserted_payout_amount"), rs.getString("status"),
        ) }, executionId,
    ))
}
