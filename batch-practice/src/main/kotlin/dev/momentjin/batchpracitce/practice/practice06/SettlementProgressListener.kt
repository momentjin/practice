package dev.momentjin.batchpracitce.practice.practice06

import dev.momentjin.batchpracitce.practice.common.settlement.SettlementPreview
import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.listener.ChunkListener
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
@Profile("practice06")
@StepScope
class SettlementProgressListener(
    private val summaries: SettlementExecutionSummaryRepository,
    private val scope: ProgressScope,
    @Value("\${practice.progress-log-every-chunks:1}") private val logEveryChunks: Int,
    @Value("#{stepExecution}") private val execution: StepExecution,
) : StepExecutionListener, ChunkListener<PurchaseOrder, SettlementPreview> {
    private val log = LoggerFactory.getLogger(javaClass)
    private var startedNanos = 0L
    private var committedChunks = 0L

    init { require(logEveryChunks > 0) { "진행 로그 주기는 양수여야 합니다." } }
    private val executionId: Long get() = execution.jobExecution.id

    // 정산 Step 시작 직전에 Reader와 같은 입력 범위로 전체 대상을 센다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun beforeStep(stepExecution: StepExecution) {
        startedNanos = System.nanoTime()
        val targetCount = summaries.countTargets(scope.orderPrefix, scope.orderNumberLength, scope.maxItems)
        val resumeFrom = stepExecution.executionContext.getInt("${scope.readerName}.read.count", 0).toLong()
        summaries.initialize(executionId, stepExecution.jobExecution.jobInstanceId, scope.dataset, targetCount, resumeFrom)
        log.info("[진행 시작] executionId={}, 전체={}건, 재시작 위치={}건", executionId, targetCount, resumeFrom)
    }

    override fun afterChunk(chunk: Chunk<SettlementPreview>) {
        // 현재 Batch 6의 이 콜백은 commit 전이다. 성공한 commit 이후에만 로그를 출력한다.
        val snapshot = summaries.find(executionId)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                committedChunks++
                if (committedChunks % logEveryChunks != 0L && snapshot.completedCount < snapshot.targetCount) return
                val elapsedSeconds = ((System.nanoTime() - startedNanos) / 1_000_000_000.0).coerceAtLeast(0.001)
                log.info("[진행] {}/{}건 ({}%), 이번 실행 신규={}, 기존={}, 제외={}, 평균 처리량={}건/초",
                    snapshot.completedCount, snapshot.targetCount, "%.1f".format(snapshot.progressPercent),
                    snapshot.insertedCount, snapshot.existingCount, snapshot.skippedCount,
                    "%.1f".format(snapshot.processedThisExecution / elapsedSeconds))
            }
        })
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun afterStep(stepExecution: StepExecution): org.springframework.batch.core.ExitStatus? {
        summaries.finish(executionId, stepExecution.status.name, (System.nanoTime() - startedNanos) / 1_000_000)
        return null
    }
}
