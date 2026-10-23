package dev.momentjin.batchpracitce.practice.common.launch

import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.launch.JobOperator
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("practice00", "practice01", "practice02", "practice03", "practice04", "practice05", "practice06", "practice07", "practice08")
@ConditionalOnProperty(prefix = "practice", name = ["launch-on-startup"], havingValue = "true", matchIfMissing = true)
class PracticeJobRunner(
    private val jobs: List<Job>,
    private val policy: PracticeParameterPolicy,
    private val operator: JobOperator,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)
    override fun run(args: ApplicationArguments) {
        require(jobs.size == 1) { "실습 프로필은 한 번에 하나만 활성화하세요." }
        // CLI → 타입/식별 여부를 명시한 JobParameters → 실제 Job 실행 경로.
        val parameters = policy.fromArguments(args.nonOptionArgs)
        val execution = operator.start(jobs.single(), parameters)
        log.info("[실행 결과] job={}, instanceId={}, executionId={}, status={}",
            jobs.single().name, execution.jobInstanceId, execution.id, execution.status)
        check(execution.status == BatchStatus.COMPLETED) { "배치 실패: executionId=${execution.id}, status=${execution.status}" }
    }
}
