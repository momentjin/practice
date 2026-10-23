package dev.momentjin.batchpracitce.practice.practice00

import dev.momentjin.batchpracitce.practice.common.launch.PracticeParameterPolicy
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager

@Configuration
@Profile("practice00")
class HelloBatchJobConfiguration {
    // Job은 작업 전체다. 지금은 Step 하나만 실행한다.
    // JobRepository는 Job/Step의 실행 상태를 BATCH_ 테이블에 기록한다.
    @Bean
    fun helloBatchJob(
        jobRepository: JobRepository,
        policy: PracticeParameterPolicy,
        @Qualifier("helloBatchStep") step: Step,
    ): Job = JobBuilder("helloBatchJob", jobRepository)
        .validator(policy)
        .start(step)
        .build()

    // Step은 작업의 한 단계다. 이 Step에서는 Tasklet을 트랜잭션 안에서 실행한다.
    @Bean
    fun helloBatchStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        tasklet: HelloTasklet,
    ): Step = StepBuilder("helloBatchStep", jobRepository)
        .tasklet(tasklet, transactionManager)
        .build()
}
