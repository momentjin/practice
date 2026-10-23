package dev.momentjin.batchpracitce.practice.common.fixture

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import kotlin.time.measureTime

@Component
@Profile("seed")
class OrderSeedRunner(private val seedService: OrderSeedService) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val values = args.getOptionValues("seed.count")
        require(values == null || values.size == 1) { "--seed.count는 한 번만 지정합니다." }
        val count = values?.single()?.toIntOrNull() ?: if (values == null) 1_000_000 else
            throw IllegalArgumentException("--seed.count는 정수여야 합니다.")
        val elapsed = measureTime { seedService.generate(count) }
        log.info("시드 완료: 목표 주문 {}건, 소요 시간 {}", count, elapsed)
    }
}
