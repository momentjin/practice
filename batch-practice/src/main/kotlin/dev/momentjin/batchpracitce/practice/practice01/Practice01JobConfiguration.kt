package dev.momentjin.batchpracitce.practice.practice01

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
@Profile("practice01")
class Practice01JobConfiguration {
    @Bean
    fun paidOrderPreviewJob(steps: PracticeSteps, policy: PracticeParameterPolicy, practice01Step: Step): Job =
        JobBuilder("paidOrderPreviewJob", steps.repository)
            .validator(policy)
            .start(steps.prepare("practice01PrepareStep", 50))
            .next(practice01Step)
            .build()

    @Bean
    fun practice01Step(
        steps: PracticeSteps,
        practiceOrderReader: JpaPagingItemReader<PurchaseOrder>,
        processor: SettlementPreviewProcessor,
        writer: SettlementPreviewLogWriter,
    ): Step = steps.chunk("practice01Step")
        .reader(practiceOrderReader)
        .processor(processor)
        .writer(writer)
        .build()
}
