package dev.momentjin.batchpracitce.practice.practice07

import dev.momentjin.batchpracitce.domain.order.*
import dev.momentjin.batchpracitce.practice.common.fixture.PracticeDataset
import dev.momentjin.batchpracitce.practice.common.launch.PracticeParameterPolicy
import dev.momentjin.batchpracitce.practice.common.settlement.*
import dev.momentjin.batchpracitce.practice.common.step.PracticeSteps
import jakarta.persistence.EntityManagerFactory
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@Profile("practice07")
class PartitionPracticeJobConfiguration {
    @Bean
    fun partitionExecutor() = ThreadPoolTaskExecutor().apply {
        corePoolSize = 4
        maxPoolSize = 4
        queueCapacity = 4
        setThreadNamePrefix("practice07-worker-")
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(30)
    }

    @Bean
    fun partitionPracticeJob(steps: PracticeSteps, policy: PracticeParameterPolicy, partitionManagerStep: Step): Job =
        JobBuilder("partitionPracticeJob", steps.repository).validator(policy)
            .start(steps.prepare("practice07PrepareStep", 80)).next(partitionManagerStep).build()

    @Bean
    fun partitionManagerStep(
        steps: PracticeSteps, partitionWorkerStep: Step, partitionExecutor: ThreadPoolTaskExecutor,
        partitioner: DatasetPartitioner,
    ): Step = StepBuilder("partitionManagerStep", steps.repository)
        .partitioner(partitionWorkerStep.name, partitioner)
        .step(partitionWorkerStep).gridSize(8).taskExecutor(partitionExecutor).build()

    @Bean
    @StepScope
    fun partitioner(@Value("#{jobParameters['dataset']}") dataset: String) = DatasetPartitioner(dataset)

    @Bean
    fun partitionWorkerStep(
        steps: PracticeSteps, partitionOrderReader: JpaPagingItemReader<PurchaseOrder>,
        processor: SettlementPreviewProcessor, writer: SettlementResultWriter,
    ): Step = steps.chunk("partitionWorkerStep", 5)
        .reader(partitionOrderReader).processor(processor).writer(writer)
        .listener(object : StepExecutionListener {
            override fun beforeStep(stepExecution: StepExecution) {
                val thread = Thread.currentThread().name
                val context = stepExecution.executionContext
                context.putString("worker.thread", thread)
                LoggerFactory.getLogger(PartitionPracticeJobConfiguration::class.java)
                    .info("[Worker] 파티션={}, 범위={}~{}, thread={}", stepExecution.stepName,
                        context.getString("fromOrderNumber"), context.getString("toOrderNumber"), thread)
            }
        }).build()

    @Bean
    @StepScope
    fun partitionOrderReader(
        entityManagerFactory: EntityManagerFactory,
        @Value("#{stepExecutionContext['fromOrderNumber']}") from: String,
        @Value("#{stepExecutionContext['toOrderNumber']}") to: String,
    ): JpaPagingItemReader<PurchaseOrder> = JpaPagingItemReaderBuilder<PurchaseOrder>()
        .name("partitionOrderReader").entityManagerFactory(entityManagerFactory)
        .queryString("select o from PurchaseOrder o where o.status = :status and o.orderNumber between :from and :to and length(o.orderNumber) = :length order by o.id asc")
        .parameterValues(mapOf("status" to OrderStatus.PAID, "from" to from, "to" to to, "length" to from.length))
        .pageSize(7).saveState(true).build()
}

// Manager Step 실행 시점에 dataset을 주입받는다. Worker에서 JobScope를 참조하지 않는다.
open class DatasetPartitioner(dataset: String) : org.springframework.batch.core.partition.Partitioner {
    private val delegate = OrderRangePartitioner(PracticeDataset(dataset))
    override fun partition(gridSize: Int) = delegate.partition(gridSize)
}
