package dev.momentjin.batchpracitce.practice.practice02

import org.springframework.batch.core.job.parameters.InvalidJobParametersException
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.job.parameters.JobParametersValidator

/** 입력값과 실행 식별 정책. 같은 작업 = Job 이름 + merchant.id. */
data class MerchantOrderPreviewJobParametersSpec(
    val merchantId: Long,
    val requestedBy: String,
) {
    init {
        if (merchantId <= 0) throw InvalidJobParametersException("merchant.id는 양의 정수여야 합니다.")
        if (requestedBy.isBlank()) throw InvalidJobParametersException("requestedBy는 공백일 수 없습니다.")
    }

    fun toJobParameters(): JobParameters = JobParametersBuilder()
        .addLong(MERCHANT_ID, merchantId, true)
        .addString(REQUESTED_BY, requestedBy, false)
        .toJobParameters()

    companion object {
        const val MERCHANT_ID = "merchant.id"
        const val REQUESTED_BY = "requestedBy"
        private val REQUIRED_KEYS = setOf(MERCHANT_ID, REQUESTED_BY)

        // 다른 실행 경로에서도 이름·타입·식별 여부·값의 정책을 동일하게 검사한다.
        val validator = JobParametersValidator { parameters ->
            val values = parameters.parameters.associateBy { it.name }
            validateKeys(values.keys)
            val merchant = values.getValue(MERCHANT_ID)
            val requester = values.getValue(REQUESTED_BY)
            if (merchant.type != Long::class.javaObjectType || !merchant.identifying) {
                throw InvalidJobParametersException("merchant.id는 Long 타입의 식별 파라미터여야 합니다.")
            }
            if (requester.type != String::class.java || requester.identifying) {
                throw InvalidJobParametersException("requestedBy는 String 타입의 비식별 파라미터여야 합니다.")
            }
            MerchantOrderPreviewJobParametersSpec(
                merchant.value as Long,
                requester.value as String
            )
        }

        fun fromArguments(arguments: List<String>): MerchantOrderPreviewJobParametersSpec {
            val values = dev.momentjin.batchpracitce.practice.common.launch.CliArguments.parse(arguments)
            validateKeys(values.keys)
            val merchantId = values.getValue(MERCHANT_ID).toLongOrNull()
                ?: throw InvalidJobParametersException("merchant.id는 Long 범위의 양의 정수여야 합니다.")
            return MerchantOrderPreviewJobParametersSpec(merchantId, values.getValue(REQUESTED_BY))
        }

        private fun validateKeys(keys: Set<String>) {
            if (keys != REQUIRED_KEYS) {
                throw InvalidJobParametersException("필수 파라미터=$REQUIRED_KEYS, 전달한 파라미터=$keys")
            }
        }
    }
}
