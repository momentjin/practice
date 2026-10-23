package dev.momentjin.batchpracitce.practice.practice03

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
import dev.momentjin.batchpracitce.practice.practice03.ThirdChunkFailureWriter

@Configuration
@Profile("practice03")
class Practice03JobConfiguration {
    @Bean
    fun restartPracticeJob(steps: PracticeSteps, policy: PracticeParameterPolicy, practice03Step: Step): Job =
        JobBuilder("restartPracticeJob", steps.repository)
            .validator(policy)
            .start(steps.prepare("practice03PrepareStep", 50))
            .next(practice03Step)
            .build()

    @Bean
    fun practice03Step(
        steps: PracticeSteps,
        practiceOrderReader: JpaPagingItemReader<PurchaseOrder>,
        processor: SettlementPreviewProcessor,
        writer: ThirdChunkFailureWriter,
    ): Step = steps.chunk("practice03Step")
        .reader(practiceOrderReader)
        .processor(processor)
        .writer(writer)
        .build()
}
