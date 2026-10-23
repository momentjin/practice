package dev.momentjin.batchpracitce.practice.practice06

import dev.momentjin.batchpracitce.practice.common.fixture.PracticeDataset
import dev.momentjin.batchpracitce.practice.common.reader.PracticeReaderConfiguration
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("practice06")
@StepScope
class ProgressScope(
    @Value("#{jobParameters['dataset']}") val dataset: String,
    @Value("\${practice.max-items:50}") val maxItems: Int,
) {
    private val input = PracticeDataset(dataset)
    val orderPrefix get() = input.prefix
    val orderNumberLength get() = input.orderNumberLength
    val readerName get() = PracticeReaderConfiguration.READER_NAME
}
