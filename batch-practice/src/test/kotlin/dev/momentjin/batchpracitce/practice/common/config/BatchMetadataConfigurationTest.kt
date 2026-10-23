package dev.momentjin.batchpracitce.practice.common.config

import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(properties = [
    "spring.batch.job.enabled=false",
    "practice.launch-on-startup=false",
    "spring.batch.jdbc.initialize-schema=always",
    "spring.datasource.url=jdbc:h2:mem:json_context_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
])
@ActiveProfiles("test", "practice00")
class BatchMetadataConfigurationTest @Autowired constructor(
    private val operator: JobOperator,
    @Qualifier("helloBatchJob") private val job: Job,
    private val repository: JobRepository,
    private val jdbc: JdbcTemplate,
) {
    @Test
    fun `Boot 저장소는 Job과 Step Context에 JSON을 사용하고 기존 Context도 조회한다`() {
        val parameters = dev.momentjin.batchpracitce.practice.common.launch.PracticeParameters(
            dev.momentjin.batchpracitce.practice.common.fixture.PracticeDataset("json-context"), 1L,
        ).toJobParameters()
        val execution = operator.start(job, parameters)
        assertEquals(BatchStatus.COMPLETED, execution.status)
        val jobContext = jdbc.queryForObject(
            "SELECT SHORT_CONTEXT FROM BATCH_JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID=?", String::class.java, execution.id,
        )!!
        val stepContext = jdbc.queryForObject(
            "SELECT SHORT_CONTEXT FROM BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID=?", String::class.java,
            execution.stepExecutions.single().id,
        )!!
        for (context in listOf(jobContext, stepContext)) {
            JsonMapper.builder().build().readTree(context)
            assertTrue(context.contains("batch.version"))
        }
        val legacy = "rO0ABXNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAAx3CAAAABAAAAABdAANYmF0Y2gudmVyc2lvbnQABTYuMC42eA=="
        jdbc.update("UPDATE BATCH_JOB_EXECUTION_CONTEXT SET SHORT_CONTEXT=? WHERE JOB_EXECUTION_ID=?", legacy, execution.id)
        val loaded = repository.getJobExecution(execution.id)!!
        assertEquals("6.0.6", loaded.executionContext.getString("batch.version"))
    }
}
