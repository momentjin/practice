package dev.momentjin.batchpracitce.practice.common.reader

import dev.momentjin.batchpracitce.domain.order.OrderStatus
import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import dev.momentjin.batchpracitce.practice.common.fixture.PracticeDataset
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("practice01", "practice03", "practice04", "practice05", "practice06", "practice10")
class PracticeReaderConfiguration {
    @Bean
    @StepScope
    fun practiceOrderReader(
        entityManagerFactory: EntityManagerFactory,
        @Value("#{jobParameters['dataset']}") datasetName: String,
        @Value("\${practice.max-items:50}") maxItems: Int,
    ): JpaPagingItemReader<PurchaseOrder> {
        require(maxItems in 1..50)
        val dataset = PracticeDataset(datasetName)
        return JpaPagingItemReaderBuilder<PurchaseOrder>()
            .name(READER_NAME)
            .entityManagerFactory(entityManagerFactory)
            .queryString("select o from PurchaseOrder o where o.status = :status and o.orderNumber like :prefix and length(o.orderNumber) = :length order by o.id asc")
            .parameterValues(mapOf("status" to OrderStatus.PAID, "prefix" to "${dataset.prefix}%", "length" to dataset.orderNumberLength))
            .pageSize(7) // DB 조회 단위. Chunk 크기와 달라도 된다.
            .maxItemCount(maxItems)
            .saveState(true)
            .build()
    }
    companion object { const val READER_NAME = "practiceOrderReader" }
}
