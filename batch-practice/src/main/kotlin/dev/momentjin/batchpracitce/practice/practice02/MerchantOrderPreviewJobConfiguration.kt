package dev.momentjin.batchpracitce.practice.practice02

import dev.momentjin.batchpracitce.domain.order.OrderStatus
import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import dev.momentjin.batchpracitce.practice.common.launch.PracticeParameterPolicy
import dev.momentjin.batchpracitce.practice.common.settlement.*
import dev.momentjin.batchpracitce.practice.common.step.PracticeSteps
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.step.Step
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("practice02")
class MerchantOrderPreviewJobConfiguration {
    @Bean
    fun merchantOrderPreviewJob(steps: PracticeSteps, policy: PracticeParameterPolicy, merchantOrderPreviewStep: Step): Job =
        JobBuilder("merchantOrderPreviewJob", steps.repository)
            .validator(policy).start(merchantOrderPreviewStep).build()

    @Bean
    fun merchantOrderPreviewStep(
        steps: PracticeSteps, merchantOrderReader: JpaPagingItemReader<PurchaseOrder>,
        processor: SettlementPreviewProcessor, writer: SettlementPreviewLogWriter,
    ): Step = steps.chunk("merchantOrderPreviewStep")
        .reader(merchantOrderReader).processor(processor).writer(writer).build()

    @Bean
    @StepScope
    fun merchantOrderReader(
        entityManagerFactory: EntityManagerFactory,
        @Value("#{jobParameters['merchant.id']}") merchantId: Long,
    ): JpaPagingItemReader<PurchaseOrder> = JpaPagingItemReaderBuilder<PurchaseOrder>()
        .name("merchantOrderReader").entityManagerFactory(entityManagerFactory)
        .queryString("select o from PurchaseOrder o where o.status = :status and o.merchant.id = :merchantId order by o.id asc")
        .parameterValues(mapOf("status" to OrderStatus.PAID, "merchantId" to merchantId))
        .pageSize(7).maxItemCount(25).saveState(true).build()
}
