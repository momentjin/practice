package dev.momentjin.batchpracitce.domain.order

import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<PurchaseOrder, Long> {
    fun findByOrderNumber(orderNumber: String): PurchaseOrder?
}
