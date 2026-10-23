package dev.momentjin.batchpracitce.practice.practice10

import dev.momentjin.batchpracitce.domain.order.OrderStatus
import dev.momentjin.batchpracitce.domain.order.PurchaseOrder
import dev.momentjin.batchpracitce.domain.settlement.SettlementCalculator
import dev.momentjin.batchpracitce.domain.settlement.SettlementResultRepository
import dev.momentjin.batchpracitce.practice.common.fixture.PracticeDataset
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 처리 건수만 맞는지 보지 않고, 대상별 누락과 금액을 원본에서 다시 계산해 검증한다. */
@Service
@Profile("practice10")
class SettlementVerificationService(
    private val entityManager: EntityManager,
    private val results: SettlementResultRepository,
) {
    @Transactional(readOnly = true)
    fun verify(dataset: PracticeDataset, expectedCount: Int = 50): VerifiedSettlement {
        require(expectedCount > 0)
        val orders = entityManager.createQuery(
            "select o from PurchaseOrder o where o.orderNumber like :prefix and length(o.orderNumber) = :length order by o.id asc",
            PurchaseOrder::class.java,
        ).setParameter("prefix", "${dataset.prefix}%").setParameter("length", dataset.orderNumberLength).resultList
        check(orders.size == expectedCount && orders.all { it.status == OrderStatus.PAID }) {
            "처리 대상 불일치: 예상=$expectedCount, 실제=${orders.size}"
        }
        val byId = orders.associateBy { requireNotNull(it.id) }
        val stored = results.findAllByOrderIdIn(byId.keys)
        check(stored.size == expectedCount && stored.map { it.orderId }.toSet() == byId.keys) {
            "정산 결과 누락 또는 중복: 대상=$expectedCount, 결과=${stored.size}"
        }
        stored.forEach { result ->
            val expected = SettlementCalculator.calculate(byId.getValue(result.orderId).amount)
            check(result.hasSameAmounts(expected.orderAmount, expected.feeAmount, expected.payoutAmount)) {
                "정산 금액 불일치: orderId=${result.orderId}"
            }
        }
        val orderTotal = orders.sumOf { it.amount }
        val feeTotal = stored.sumOf { it.feeAmount }
        val payoutTotal = stored.sumOf { it.payoutAmount }
        check(orderTotal == feeTotal + payoutTotal) { "정산 합계 불일치" }
        return VerifiedSettlement(expectedCount, orderTotal, feeTotal, payoutTotal)
    }
}

data class VerifiedSettlement(val count: Int, val orderAmount: Long, val feeAmount: Long, val payoutAmount: Long)
