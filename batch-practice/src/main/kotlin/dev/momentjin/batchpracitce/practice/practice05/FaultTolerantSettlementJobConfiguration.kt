package dev.momentjin.batchpracitce.practice.practice05

import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import dev.momentjin.batchpracitce.practice.common.launch.PracticeParameterPolicy
import dev.momentjin.batchpracitce.practice.common.settlement.*
import dev.momentjin.batchpracitce.practice.common.step.PracticeSteps
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.listener.SkipListener
import org.springframework.batch.core.step.Step
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("practice05")
class FaultTolerantSettlementJobConfiguration {
    @Bean
    fun faultTolerantSettlementJob(steps: PracticeSteps, policy: PracticeParameterPolicy, faultTolerantSettlementStep: Step): Job =
        JobBuilder("faultTolerantSettlementJob", steps.repository).validator(policy)
            .start(steps.prepare("practice05PrepareStep", 50)).next(faultTolerantSettlementStep).build()

    @Bean
    fun faultTolerantSettlementStep(
        steps: PracticeSteps, practiceOrderReader: JpaPagingItemReader<PurchaseOrder>,
        processor: FaultTolerantSettlementProcessor, writer: SettlementResultWriter,
    ): Step = steps.chunk("faultTolerantSettlementStep")
        .reader(practiceOrderReader).processor(processor).writer(writer)
        .faultTolerant()
        .retry(TemporaryProcessingException::class.java).retryLimit(2)
        .skip(InvalidOrderDataException::class.java).skipLimit(2)
        .listener(object : SkipListener<PurchaseOrder, SettlementPreview> {
            override fun onSkipInProcess(item: PurchaseOrder, t: Throwable) {
                LoggerFactory.getLogger(FaultTolerantSettlementJobConfiguration::class.java)
                    .warn("[Skip] 주문={}, 원인={}", item.orderNumber, t.message)
            }
        })
        .build()
}
