package dev.momentjin.batchpracitce.practice.common.settlement

import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import dev.momentjin.batchpracitce.domain.settlement.SettlementCalculator
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component

/** 상태 없는 공용 계산 Processor. 오류 주입과 Filter는 해당 과제에서만 추가한다. */
@Component
class SettlementPreviewProcessor : ItemProcessor<PurchaseOrder, SettlementPreview> {
    override fun process(item: PurchaseOrder): SettlementPreview {
        val amounts = SettlementCalculator.calculate(item.amount)
        return SettlementPreview(requireNotNull(item.id), item.orderNumber,
            amounts.orderAmount, amounts.feeAmount, amounts.payoutAmount)
    }
}
