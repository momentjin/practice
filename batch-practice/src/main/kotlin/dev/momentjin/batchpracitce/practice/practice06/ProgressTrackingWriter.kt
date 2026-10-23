package dev.momentjin.batchpracitce.practice.practice06

import dev.momentjin.batchpracitce.practice.common.settlement.*
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("practice06")
@StepScope
class ProgressTrackingWriter(
    private val delegate: SettlementResultWriter,
    private val summaries: SettlementExecutionSummaryRepository,
    @Value("#{stepExecution.jobExecution.id}") private val executionId: Long,
    @Value("\${practice.force-fail:false}") private val forceFail: Boolean,
) : ItemWriter<SettlementPreview> {
    private var chunkNumber = 0
    override fun write(chunk: Chunk<out SettlementPreview>) {
        if (chunk.isEmpty) return
        val result = delegate.writeAndReport(chunk)
        // 정산 저장과 집계 UPDATE는 같은 Chunk 트랜잭션에서 함께 커밋/롤백한다.
        summaries.addWritten(executionId, result)
        chunkNumber++
        if (forceFail && chunkNumber == 3) error("진행 집계 롤백 확인: 세 번째 Chunk 실패")
    }
}
