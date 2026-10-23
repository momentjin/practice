package dev.momentjin.batchpracitce.practice.practice00

import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("practice00")
class HelloTasklet : Tasklet {
    private val log = LoggerFactory.getLogger(javaClass)

    // Tasklet 안에 실제 수행할 일을 작성한다. 첫 실습은 로그 출력만 한다.
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val stepExecution = contribution.stepExecution

        log.info("첫 배치 실행: job={}, step={}", stepExecution.jobExecution.jobInstance.jobName, stepExecution.stepName)
        log.info("실행 이력: jobExecutionId={}, stepExecutionId={}", stepExecution.jobExecution.id, stepExecution.id)
        log.info("실행 파라미터: {}", chunkContext.stepContext.jobParameters)

        // 현재는 별도 executor를 설정하지 않았다. 병렬화 전 비교할 기준이다.
        log.info("실행 스레드: {}", Thread.currentThread().name)

        // 이번 Tasklet의 작업이 끝났음을 알린다. Job 전체의 성공 여부는 프레임워크가 결정한다.
        return RepeatStatus.FINISHED
    }
}
