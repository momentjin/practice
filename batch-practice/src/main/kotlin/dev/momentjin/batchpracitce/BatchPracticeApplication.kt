package dev.momentjin.batchpracitce

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BatchPracticeApplication

fun main(args: Array<String>) {
	// 이 프로젝트의 ApplicationRunner는 배치 완료까지 동기적으로 기다린다.
	runApplication<BatchPracticeApplication>(*args).use {
		// CLI 작업이 끝나면 Context를 닫아 스레드풀과 DB 연결도 정리한다.
	}
}
