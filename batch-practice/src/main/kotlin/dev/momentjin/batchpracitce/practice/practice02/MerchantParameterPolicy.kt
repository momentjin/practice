package dev.momentjin.batchpracitce.practice.practice02

import dev.momentjin.batchpracitce.practice.common.launch.PracticeParameterPolicy
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("practice02")
class MerchantParameterPolicy : PracticeParameterPolicy {
    override fun fromArguments(arguments: List<String>): JobParameters =
        MerchantOrderPreviewJobParametersSpec.fromArguments(arguments).toJobParameters()
    override fun validate(parameters: JobParameters) = MerchantOrderPreviewJobParametersSpec.validator.validate(parameters)
}
