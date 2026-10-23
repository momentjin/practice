package dev.momentjin.batchpracitce.practice.common.config

import org.springframework.batch.core.repository.ExecutionContextSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class BatchMetadataConfiguration {
    // Boot가 이 Bean을 JDBC 배치 저장소에 연결한다. 배치 자동 설정은 유지한다.
    @Bean
    fun executionContextSerializer(): ExecutionContextSerializer = ReadableExecutionContextSerializer()
}
