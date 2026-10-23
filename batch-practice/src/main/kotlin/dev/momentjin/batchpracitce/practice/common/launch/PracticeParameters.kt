package dev.momentjin.batchpracitce.practice.common.launch

import dev.momentjin.batchpracitce.practice.common.fixture.PracticeDataset
import org.springframework.batch.core.job.parameters.*
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

interface PracticeParameterPolicy : JobParametersValidator {
    fun fromArguments(arguments: List<String>): JobParameters
}

object CliArguments {
    fun parse(arguments: List<String>): Map<String, String> {
        val values = linkedMapOf<String, String>()
        arguments.forEach { argument ->
            val pair = argument.split('=', limit = 2)
            if (pair.size != 2 || pair[0].isBlank() || values.putIfAbsent(pair[0], pair[1]) != null) {
                throw InvalidJobParametersException("이름=값 형식과 중복 없는 키를 사용하세요: $argument")
            }
        }
        return values
    }
}

/** JobInstance = Job 이름 + dataset + run.id. requestedBy는 실행 기록용 비식별 값. */
data class PracticeParameters(val dataset: PracticeDataset, val runNumber: Long, val requestedBy: String = "local") {
    init {
        require(runNumber > 0) { "run.id는 양의 정수입니다." }
        require(requestedBy.isNotBlank()) { "requestedBy는 비어 있을 수 없습니다." }
    }
    fun toJobParameters(): JobParameters = JobParametersBuilder()
        .addString("dataset", dataset.name, true)
        .addLong("run.id", runNumber, true)
        .addString("requestedBy", requestedBy, false)
        .toJobParameters()

    companion object {
        fun from(parameters: JobParameters): PracticeParameters {
            val values = parameters.parameters.associateBy { it.name }
            if (values.keys != setOf("dataset", "run.id", "requestedBy")) {
                throw InvalidJobParametersException("허용 파라미터는 dataset, run.id, requestedBy입니다.")
            }
            val dataset = values.getValue("dataset")
            val run = values.getValue("run.id")
            val requester = values.getValue("requestedBy")
            if (dataset.type != String::class.java || !dataset.identifying ||
                run.type != Long::class.javaObjectType || !run.identifying ||
                requester.type != String::class.java || requester.identifying) {
                throw InvalidJobParametersException("dataset:String/식별, run.id:Long/식별, requestedBy:String/비식별이어야 합니다.")
            }
            return checked { PracticeParameters(PracticeDataset(dataset.value as String), run.value as Long, requester.value as String) }
        }

        fun parse(arguments: List<String>): PracticeParameters {
            val values = CliArguments.parse(arguments)
            if (!values.keys.containsAll(setOf("dataset", "run.id")) ||
                (values.keys - setOf("dataset", "run.id", "requestedBy")).isNotEmpty()) {
                throw InvalidJobParametersException("dataset과 run.id는 필수이며 requestedBy만 추가할 수 있습니다.")
            }
            val run = values.getValue("run.id").takeIf { it.matches(Regex("[1-9][0-9]*")) }?.toLongOrNull()
                ?: throw InvalidJobParametersException("run.id는 앞에 0이 없는 양의 Long 정수입니다.")
            return checked { PracticeParameters(PracticeDataset(values.getValue("dataset")), run, values["requestedBy"] ?: "local") }
        }

        private fun checked(block: () -> PracticeParameters): PracticeParameters = try { block() }
        catch (exception: IllegalArgumentException) {
            throw InvalidJobParametersException(exception.message ?: "잘못된 실행 파라미터입니다.")
        }
    }
}

@Component
@Profile("!practice02")
class StandardPracticeParameterPolicy : PracticeParameterPolicy {
    override fun fromArguments(arguments: List<String>) = PracticeParameters.parse(arguments).toJobParameters()
    override fun validate(parameters: JobParameters) { PracticeParameters.from(parameters) }
}
