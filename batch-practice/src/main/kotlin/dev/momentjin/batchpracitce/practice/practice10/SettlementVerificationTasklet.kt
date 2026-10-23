package dev.momentjin.batchpracitce.practice.practice10

import dev.momentjin.batchpracitce.practice.common.fixture.PracticeDataset
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("practice10")
@StepScope
class SettlementVerificationTasklet(
    private val verifier: SettlementVerificationService,
    @Value("#{jobParameters['dataset']}") datasetName: String,
    @Value("\${practice.validation.expected-count:50}") private val expectedCount: Int,
) : Tasklet {
    private val dataset = PracticeDataset(datasetName)
    private val log = LoggerFactory.getLogger(javaClass)
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val checked = verifier.verify(dataset, expectedCount)
        log.info("[검증 통과] 대상={}, 주문금액={}, 수수료={}, 지급액={}",
            checked.count, checked.orderAmount, checked.feeAmount, checked.payoutAmount)
        return RepeatStatus.FINISHED
    }
}
