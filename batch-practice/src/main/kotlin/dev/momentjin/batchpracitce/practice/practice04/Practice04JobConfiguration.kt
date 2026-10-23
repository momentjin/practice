package dev.momentjin.batchpracitce.practice.practice04

import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import dev.momentjin.batchpracitce.practice.common.launch.PracticeParameterPolicy
import dev.momentjin.batchpracitce.practice.common.settlement.*
import dev.momentjin.batchpracitce.practice.common.step.PracticeSteps
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.step.Step
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("practice04")
class Practice04JobConfiguration {
    @Bean
    fun idempotentSettlementJob(steps: PracticeSteps, policy: PracticeParameterPolicy, practice04Step: Step): Job =
        JobBuilder("idempotentSettlementJob", steps.repository)
            .validator(policy)
            .start(steps.prepare("practice04PrepareStep", 50))
            .next(practice04Step)
            .build()

    @Bean
    fun practice04Step(
        steps: PracticeSteps,
        practiceOrderReader: JpaPagingItemReader<PurchaseOrder>,
        processor: SettlementPreviewProcessor,
        writer: SettlementResultWriter,
    ): Step = steps.chunk("practice04Step")
        .reader(practiceOrderReader)
        .processor(processor)
        .writer(writer)
        .build()
}
