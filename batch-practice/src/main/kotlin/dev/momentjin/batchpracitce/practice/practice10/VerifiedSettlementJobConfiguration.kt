package dev.momentjin.batchpracitce.practice.practice10

import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import dev.momentjin.batchpracitce.practice.common.launch.*
import dev.momentjin.batchpracitce.practice.common.settlement.*
import dev.momentjin.batchpracitce.practice.common.step.PracticeSteps
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("practice10")
class VerifiedSettlementJobConfiguration {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun verifiedSettlementJob(
        steps: PracticeSteps, policy: PracticeParameterPolicy,
        verifiedSettlementStep: Step, settlementVerificationStep: Step,
    ): Job = JobBuilder(SETTLEMENT_JOB, steps.repository).validator(policy)
        .start(steps.prepare("practice10PrepareStep", 50))
        .next(verifiedSettlementStep)
        .next(settlementVerificationStep)
        .build()

    @Bean
    fun verifiedSettlementStep(
        steps: PracticeSteps, practiceOrderReader: JpaPagingItemReader<PurchaseOrder>,
        processor: SettlementPreviewProcessor, writer: SettlementResultWriter,
    ): Step = steps.chunk("verifiedSettlementStep")
        .reader(practiceOrderReader).processor(processor).writer(writer).build()

    @Bean
    fun settlementVerificationStep(steps: PracticeSteps, tasklet: SettlementVerificationTasklet): Step =
        StepBuilder("settlementVerificationStep", steps.repository)
            .tasklet(tasklet, steps.transactionManager).build()

    @Bean
    fun payoutPreviewJob(steps: PracticeSteps, policy: PracticeParameterPolicy, verifier: SettlementVerificationService): Job =
        JobBuilder(PAYOUT_JOB, steps.repository).validator(policy)
            .start(StepBuilder("payoutPreviewStep", steps.repository).tasklet({ contribution, _ ->
                val parameters = contribution.stepExecution.jobExecution.jobParameters
                val input = PracticeParameters.from(parameters)
                // 지급 Job을 직접 호출하더라도 선행 검증이 완료된 동일 작업인지 확인한다.
                val instance = requireNotNull(steps.repository.getJobInstance(SETTLEMENT_JOB, parameters)) {
                    "선행 정산 JobInstance가 없습니다."
                }
                val upstream = requireNotNull(steps.repository.getLastJobExecution(instance))
                check(upstream.status == BatchStatus.COMPLETED) { "선행 정산·검증이 완료되지 않았습니다." }
                val checked = verifier.verify(input.dataset)
                contribution.stepExecution.executionContext.putLong("upstream.execution.id", upstream.id)
                log.info("[지급 미리보기] upstreamExecutionId={}, 대상={}, 지급액={}", upstream.id, checked.count, checked.payoutAmount)
                // 실제 송금은 하지 않는다. 외부 지급 API의 멱등성은 별도 업무 설계다.
                RepeatStatus.FINISHED
            }, steps.transactionManager).build()).build()

    companion object {
        const val SETTLEMENT_JOB = "verifiedSettlementJob"
        const val PAYOUT_JOB = "payoutPreviewJob"
    }
}
