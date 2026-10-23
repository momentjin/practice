package dev.momentjin.batchpracitce.practice.practice10

import dev.momentjin.batchpracitce.practice.common.launch.PracticeParameterPolicy
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** 같은 Job의 Step 연결과 서로 다른 Job의 순차 실행을 함께 보여준다. */
@Component
@Profile("practice10")
@ConditionalOnProperty(prefix = "practice", name = ["launch-on-startup"], havingValue = "true", matchIfMissing = true)
class SettlementWorkflowRunner(
    private val operator: JobOperator,
    private val repository: JobRepository,
    private val policy: PracticeParameterPolicy,
    @Qualifier("verifiedSettlementJob") private val settlement: Job,
    @Qualifier("payoutPreviewJob") private val payout: Job,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)
    override fun run(args: ApplicationArguments) {
        val parameters = policy.fromArguments(args.nonOptionArgs)
        val previous = repository.getJobInstance(settlement.name, parameters)?.let(repository::getLastJobExecution)
        // 지급 Job만 실패한 경우, 이미 완료된 정산 Job을 다시 시작하지 않는다.
        val upstream = if (previous?.status == BatchStatus.COMPLETED) previous else operator.start(settlement, parameters)
        check(upstream.status == BatchStatus.COMPLETED) { "정산·검증 실패로 지급을 차단합니다: ${upstream.id}" }
        val downstream = operator.start(payout, parameters)
        check(downstream.status == BatchStatus.COMPLETED) { "지급 미리보기 실패: ${downstream.id}" }
        log.info("[워크플로 완료] 정산 executionId={}, 지급 executionId={}", upstream.id, downstream.id)
    }
}
