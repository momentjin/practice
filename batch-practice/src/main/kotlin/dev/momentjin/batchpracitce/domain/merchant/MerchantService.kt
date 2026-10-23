package dev.momentjin.batchpracitce.domain.merchant

import dev.momentjin.batchpracitce.domain.merchant.Merchant
import dev.momentjin.batchpracitce.domain.merchant.MerchantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MerchantService(private val merchants: MerchantRepository) {
    @Transactional
    fun register(code: String, name: String): MerchantView =
        MerchantView.from(merchants.save(Merchant(code, name)))

    fun findByCode(code: String): MerchantView = MerchantView.from(
        merchants.findByCode(code) ?: throw NoSuchElementException("가맹점을 찾을 수 없습니다: $code"),
    )
}

data class MerchantView(val id: Long, val code: String, val name: String) {
    companion object {
        fun from(merchant: Merchant) = MerchantView(requireNotNull(merchant.id), merchant.code, merchant.name)
    }
}
