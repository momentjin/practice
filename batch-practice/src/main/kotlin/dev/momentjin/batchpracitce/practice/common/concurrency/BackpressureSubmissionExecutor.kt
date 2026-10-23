package dev.momentjin.batchpracitce.practice.common.concurrency

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** 고정 크기 스레드풀과 AbortPolicy를 사용하는 이번 실습의 제출 제어기. */
class BackpressureSubmissionExecutor(
    private val delegate: ThreadPoolTaskExecutor,
    maxInFlight: Int,
) : TaskExecutor {
    private val log = LoggerFactory.getLogger(javaClass)
    // 실행 중 4개 + 큐 대기 2개 = 미완료 작업 최대 6개에 허가증을 준다.
    private val permits = Semaphore(maxInFlight.also { require(it > 0) }, true)

    override fun execute(task: Runnable) {
        try {
            acquirePermit()
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TaskRejectedException("작업 제출을 기다리는 중 중단되었습니다.", exception)
        }

        var submitted = false
        try {
            val wrapped = Runnable {
                try {
                    task.run()
                } finally {
                    // execute()가 반환될 때가 아니라, 실제 작업이 끝날 때 돌려준다.
                    permits.release()
                }
            }
            submitWhenReady(wrapped)
            submitted = true
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TaskRejectedException("작업 제출을 기다리는 중 중단되었습니다.", exception)
        } finally {
            // 제출이 실패하면 wrapped가 실행되지 않으므로 여기서 돌려준다.
            if (!submitted) permits.release()
        }
    }

    private fun acquirePermit() {
        log.info(
            "[백프레셔 제출 요청] submitter={}, active={}, queued={}, permits={}",
            Thread.currentThread().name, delegate.activeCount, delegate.queueSize, permits.availablePermits(),
        )
        if (permits.availablePermits() == 0) {
            log.info("[백프레셔 제출 대기] 미완료 작업 한도 도달, submitter={}", Thread.currentThread().name)
        }
        while (true) {
            ensureRunning()
            // 무기한 acquire 대신 주기적으로 Executor 종료 여부도 확인한다.
            if (permits.tryAcquire(100, TimeUnit.MILLISECONDS)) return
        }
    }

    private fun submitWhenReady(task: Runnable) {
        while (true) {
            ensureRunning()
            try {
                delegate.execute(task)
                log.info("[백프레셔 제출 완료] active={}, queued={}", delegate.activeCount, delegate.queueSize)
                return
            } catch (exception: TaskRejectedException) {
                if (delegate.threadPoolExecutor.isShutdown) throw exception
                // 허가증은 작업의 finally에서 반환된다. 그 직후에도 기존 스레드가
                // 아직 큐의 다음 작업을 꺼내지 않았다면 잠깐 포화 상태일 수 있다.
                // 같은 작업이 수락될 때까지 잠시 기다린다. 거절된 작업은 실행되지 않았다.
                TimeUnit.MILLISECONDS.sleep(10)
            }
        }
    }

    private fun ensureRunning() {
        if (delegate.threadPoolExecutor.isShutdown) {
            throw TaskRejectedException("Executor가 종료되어 새 작업을 제출할 수 없습니다.")
        }
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("작업 제출 스레드가 중단되었습니다.")
        }
    }
}
