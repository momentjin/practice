package dev.momentjin.batchpracitce.practice.practice06

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
import org.springframework.batch.core.listener.StepListener

@Configuration
@Profile("practice06")
class Practice06JobConfiguration {
    @Bean
    fun progressPracticeJob(steps: PracticeSteps, policy: PracticeParameterPolicy, practice06Step: Step): Job =
        JobBuilder("progressPracticeJob", steps.repository)
            .validator(policy)
            .start(steps.prepare("practice06PrepareStep", 50))
            .next(practice06Step)
            .build()

    @Bean
    fun practice06Step(
        steps: PracticeSteps,
        practiceOrderReader: JpaPagingItemReader<PurchaseOrder>,
        processor: SettlementPreviewProcessor,
        writer: ProgressTrackingWriter,
        progress: SettlementProgressListener,
    ): Step = steps.chunk("practice06Step")
        .reader(practiceOrderReader)
        .processor(processor)
        .writer(writer)
        .listener(progress as StepListener)
        .build()
}
