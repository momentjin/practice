package dev.momentjin.batchpracitce.domain.settlement

import org.springframework.data.jpa.repository.JpaRepository

interface SettlementResultRepository : JpaRepository<SettlementResult, Long> {
    fun findAllByOrderIdIn(orderIds: Collection<Long>): List<SettlementResult>
    fun findByOrderId(orderId: Long): SettlementResult?
}
