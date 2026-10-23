package dev.momentjin.batchpracitce.practice.practice05

import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import dev.momentjin.batchpracitce.practice.common.fixture.PracticeDataset
import dev.momentjin.batchpracitce.practice.common.settlement.*
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

class TemporaryProcessingException(message: String) : RuntimeException(message)
class InvalidOrderDataException(message: String) : RuntimeException(message)

@Component
@Profile("practice05")
@StepScope
class FaultTolerantSettlementProcessor(
    private val delegate: SettlementPreviewProcessor,
    @Value("#{jobParameters['dataset']}") datasetName: String,
    @Value("\${practice.transient-failures:2}") private val transientFailures: Int,
    @Value("\${practice.invalid-positions:13}") invalidPositions: String,
    @Value("\${practice.filter-position:21}") private val filterPosition: Int,
) : ItemProcessor<PurchaseOrder, SettlementPreview> {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dataset = PracticeDataset(datasetName)
    private var attempts = 0
    private val invalid = invalidPositions.split(',').filter { it.isNotBlank() }.map {
        requireNotNull(it.trim().toIntOrNull()) { "invalid-positions는 쉼표로 구분한 정수입니다." }
    }.toSet()
    init {
        require(transientFailures in 0..10)
        require(invalid.all { it in 1..50 })
        require(filterPosition in 0..50)
    }
    override fun process(item: PurchaseOrder): SettlementPreview? {
        // 호출 횟수 대신 준비한 주문의 고정 순번으로 대상을 정한다.
        val position = dataset.position(item.orderNumber)
        if (position == 5) {
            attempts++
            if (attempts <= transientFailures) {
                log.warn("[Retry] 주문={}, 시도={}", item.orderNumber, attempts)
                throw TemporaryProcessingException("일시 오류: ${item.orderNumber}")
            }
            log.info("[Retry 성공] 주문={}, 시도={}", item.orderNumber, attempts)
        }
        if (position in invalid) throw InvalidOrderDataException("잘못된 주문: ${item.orderNumber}")
        if (position == filterPosition) {
            log.info("[Filter] 주문={}", item.orderNumber)
            return null
        }
        return delegate.process(item)
    }
}
