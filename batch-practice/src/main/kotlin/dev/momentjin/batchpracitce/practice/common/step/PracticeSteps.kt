package dev.momentjin.batchpracitce.practice.common.step

import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import dev.momentjin.batchpracitce.practice.common.fixture.PracticeOrderFixture
import dev.momentjin.batchpracitce.practice.common.launch.PracticeParameters
import dev.momentjin.batchpracitce.practice.common.settlement.SettlementPreview
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager

/** 트랜잭션 연결과 실습 입력 준비만 공통화한다. 각 과제의 처리 흐름은 해당 설정에 남긴다. */
@Component
class PracticeSteps(
    val repository: JobRepository,
    val transactionManager: PlatformTransactionManager,
    private val fixture: PracticeOrderFixture,
) {
    fun prepare(name: String, count: Int): Step = StepBuilder(name, repository)
        .tasklet({ contribution, _ ->
            val input = PracticeParameters.from(contribution.stepExecution.jobExecution.jobParameters)
            fixture.prepare(input.dataset, count)
            RepeatStatus.FINISHED
        }, transactionManager)
        .build()

    fun chunk(name: String, size: Int = 10): ChunkOrientedStepBuilder<PurchaseOrder, SettlementPreview> =
        StepBuilder(name, repository).chunk<PurchaseOrder, SettlementPreview>(size)
            .transactionManager(transactionManager)
}
