package dev.momentjin.batchpracitce.practice.common.settlement

import org.slf4j.LoggerFactory
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.stereotype.Component

@Component
class SettlementPreviewLogWriter : ItemWriter<SettlementPreview> {
    private val log = LoggerFactory.getLogger(javaClass)

    // Writer는 한 건이 아니라 Chunk를 받는다. 마지막 묶음은 10건보다 작을 수도 있다.
    override fun write(chunk: Chunk<out SettlementPreview>) {
        if (chunk.isEmpty) return
        log.info("[Writer] 묶음 {}건 출력: 첫 주문={}, 마지막 주문={}, 스레드={}",
            chunk.size(), chunk.items.first().orderNumber, chunk.items.last().orderNumber,
            Thread.currentThread().name)
        chunk.forEach { preview ->
            log.info("[Writer] 주문ID={}, 주문번호={}, 주문금액={}, 수수료={}, 지급예정액={}",
                preview.orderId, preview.orderNumber, preview.orderAmount, preview.feeAmount, preview.payoutAmount)
        }
        // 지금은 출력만 한다. 로그는 트랜잭션이 롤백돼도 되돌릴 수 없다.
        // DB에 정산 결과를 저장하는 Writer는 다음 실습에서 구현한다.
    }
}
