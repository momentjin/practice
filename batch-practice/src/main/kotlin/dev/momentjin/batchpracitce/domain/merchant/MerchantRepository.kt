package dev.momentjin.batchpracitce.domain.merchant

import org.springframework.data.jpa.repository.JpaRepository

interface MerchantRepository : JpaRepository<Merchant, Long> {
    fun findByCode(code: String): Merchant?
}
