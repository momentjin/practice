package dev.momentjin.batchpracitce.domain.ledger

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentLedgerRepository : JpaRepository<PaymentLedger, Long> {
    fun findAllByOrderIdOrderByIdAsc(orderId: Long): List<PaymentLedger>
}
