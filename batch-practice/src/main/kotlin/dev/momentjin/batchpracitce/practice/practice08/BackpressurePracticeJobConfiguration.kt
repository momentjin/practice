package dev.momentjin.batchpracitce.practice.practice08

import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.SimpleAsyncTaskExecutor
import dev.momentjin.batchpracitce.practice.common.concurrency.BackpressureSubmissionExecutor
import dev.momentjin.batchpracitce.practice.common.launch.PracticeParameterPolicy
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@Profile("practice08")
class BackpressurePracticeJobConfiguration {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun practice08Executor(
        @Value("\${practice.practice08.queue-capacity:2}") capacity: Int,
    ) = ThreadPoolTaskExecutor().apply {
        require(capacity >= 0)
        corePoolSize = 4
        maxPoolSize = 4
        queueCapacity = capacity
        setThreadNamePrefix("practice08-pool-")
        setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(30)
    }

    @Bean
    fun practice08FrameworkExecutor() = SimpleAsyncTaskExecutor("practice08-async-").apply {
        setConcurrencyLimit(4)
        setRejectTasksWhenLimitReached(false)
        setTaskTerminationTimeout(30_000)
    }

    @Bean
    fun practice08SubmissionExecutor(
        @Qualifier("practice08Executor") pool: ThreadPoolTaskExecutor,
        practice08FrameworkExecutor: SimpleAsyncTaskExecutor,
        @Value("\${practice.submission-mode:framework}") mode: String,
    ): TaskExecutor = when (mode) {
        "framework" -> TaskExecutor { practice08FrameworkExecutor.execute(it) }
        "pool" -> BackpressureSubmissionExecutor(pool, pool.maxPoolSize + pool.queueCapacity)
        else -> throw IllegalArgumentException("submission-mode는 framework 또는 pool입니다.")
    }

    @Bean
    fun backpressurePracticeJob(
        repository: JobRepository,
        policy: PracticeParameterPolicy,
        @Qualifier("practice08ManagerStep") manager: Step,
    ): Job = JobBuilder("backpressurePracticeJob", repository)
        .validator(policy)
        .start(manager)
        .build()

    @Bean
    fun practice08ManagerStep(
        repository: JobRepository,
        @Qualifier("practice08WorkerStep") worker: Step,
        @Qualifier("practice08SubmissionExecutor") submissionExecutor: TaskExecutor,
    ): Step = StepBuilder("practice08ManagerStep", repository)
        .partitioner(worker.name) { gridSize ->
            (1..gridSize).associate { number ->
                "partition$number" to ExecutionContext().apply {
                    putInt("partitionNumber", number)
                }
            }
        }
        .step(worker)
        .gridSize(10)
        .taskExecutor(submissionExecutor)
        .build()

    @Bean
    fun practice08WorkerStep(
        repository: JobRepository,
        transactionManager: PlatformTransactionManager,
        @Qualifier("practice08WorkerTasklet") tasklet: Tasklet,
    ): Step = StepBuilder("practice08WorkerStep", repository)
        .tasklet(tasklet, transactionManager)
        .build()

    @Bean
    @StepScope
    fun practice08WorkerTasklet(
        @Value("#{stepExecutionContext['partitionNumber']}") partitionNumber: Int,
        @Value("\${practice.practice08.work-millis:2000}") workMillis: Long,
    ): Tasklet {
        require(workMillis in 1..10_000) { "work-millis는 1~10000이어야 합니다." }
        return Tasklet { contribution, _ ->
            val thread = Thread.currentThread().name
            contribution.stepExecution.executionContext.putString("worker.thread", thread)
            log.info("[08 작업 시작] partition={}, thread={}", partitionNumber, thread)
            // 느린 작업을 흉내 낸다. 주문과 정산 데이터는 변경하지 않는다.
            Thread.sleep(workMillis)
            log.info("[08 작업 완료] partition={}, thread={}", partitionNumber, thread)
            RepeatStatus.FINISHED
        }
    }
}
