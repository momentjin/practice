package dev.momentjin.batchpracitce.practice.common.settlement

import dev.momentjin.batchpracitce.practice.common.settlement.SettlementPreview
import dev.momentjin.batchpracitce.domain.settlement.SettlementResult
import dev.momentjin.batchpracitce.domain.settlement.SettlementResultRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class SettlementResultWriter(private val results: SettlementResultRepository) : ItemWriter<SettlementPreview> {
    private val log = LoggerFactory.getLogger(javaClass)

    // 새 트랜잭션을 열지 않고 Step이 시작한 Chunk 트랜잭션에 반드시 참여한다.
    @Transactional(propagation = Propagation.MANDATORY)
    override fun write(chunk: Chunk<out SettlementPreview>) {
        writeAndReport(chunk)
    }

    // 프레임워크의 write()와 별도로 실제 저장 결과를 업무 집계에 반환한다.
    @Transactional(propagation = Propagation.MANDATORY)
    fun writeAndReport(chunk: Chunk<out SettlementPreview>): SettlementWriteResult {
        if (chunk.isEmpty) {
            return SettlementWriteResult(
                0,
                0,
                0
            )
        }

        val existingByOrderId = results
            .findAllByOrderIdIn(chunk.items.map { it.orderId })
            .associateBy { it.orderId }

        val newResults = chunk.items.mapNotNull { preview ->
            val existing = existingByOrderId[preview.orderId]
            if (existing != null) {
                // 같은 주문을 다시 처리해도 결과가 같으면 아무것도 저장하지 않는다.
                // 다른 결과라면 조용히 덮어쓰지 않고 실패시켜 정책 변경 등을 확인한다.
                check(existing.hasSameAmounts(preview.orderAmount, preview.feeAmount, preview.payoutAmount)) {
                    "기존 정산 결과와 금액이 다릅니다. orderId=${preview.orderId}"
                }
                null
            } else {
                SettlementResult(preview.orderId, preview.orderAmount, preview.feeAmount, preview.payoutAmount)
            }
        }

        // flush는 SQL을 DB에 반영하지만 commit은 아니다. 실패하면 이 Chunk의 저장이 롤백된다.
        results.saveAllAndFlush(newResults)

        log.info(
            "[DB Writer] 처리={}건, 신규 저장 요청={}건, 기존 동일 결과={}건",
            chunk.size(), newResults.size, chunk.size() - newResults.size
        )

        return SettlementWriteResult(
            newResults.size,
            chunk.size() - newResults.size,
            newResults.sumOf { it.payoutAmount })
    }
}

data class SettlementWriteResult(val insertedCount: Int, val existingCount: Int, val insertedPayoutAmount: Long)
