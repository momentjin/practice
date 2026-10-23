package dev.momentjin.batchpracitce.practice.common.fixture

import dev.momentjin.batchpracitce.domain.merchant.Merchant
import dev.momentjin.batchpracitce.domain.merchant.MerchantRepository
import dev.momentjin.batchpracitce.domain.order.*
import dev.momentjin.batchpracitce.domain.ledger.PaymentLedger
import dev.momentjin.batchpracitce.domain.ledger.PaymentLedgerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class PracticeOrderFixture(
    private val merchants: MerchantRepository,
    private val orders: OrderRepository,
    private val ledgers: PaymentLedgerRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.MANDATORY)
    fun prepare(dataset: PracticeDataset, count: Int) {
        require(count in 1..99_999)
        val merchant = merchants.findByCode(dataset.merchantCode)
            ?: merchants.save(Merchant(dataset.merchantCode, "실습 ${dataset.name}"))
        var created = 0
        for (position in 1..count) {
            val number = dataset.orderNumber(position)
            val amount = 10_000L + position * 101L
            val existing = orders.findByOrderNumber(number)
            if (existing != null) {
                check(existing.status == OrderStatus.PAID && existing.amount == amount && existing.merchant.id == merchant.id) {
                    "기존 실습 주문이 예상 데이터와 다릅니다: $number"
                }
                continue
            }
            val at = LocalDateTime.of(2026, 9, 1, 12, 0).plusSeconds(position.toLong())
            val order = PurchaseOrder(number, merchant, amount, PaymentChannel.APP, at)
            order.pay(at.plusMinutes(1))
            orders.save(order)
            ledgers.save(PaymentLedger.payment(order))
            created++
        }
        log.info("[입력 준비] dataset={}, 목표={}, 신규={}", dataset.name, count, created)
    }
}
