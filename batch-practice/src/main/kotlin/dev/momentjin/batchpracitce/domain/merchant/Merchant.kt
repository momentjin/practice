package dev.momentjin.batchpracitce.domain.merchant

import jakarta.persistence.*

@Entity
@Table(name = "merchants")
class Merchant(
    @Column(nullable = false, unique = true, length = 40)
    val code: String,
    @Column(nullable = false, length = 100)
    val name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    init {
        require(code.isNotBlank() && code.length <= 40) { "가맹점 코드는 1~40자여야 합니다." }
        require(name.isNotBlank() && name.length <= 100) { "가맹점 이름은 1~100자여야 합니다." }
    }
}
