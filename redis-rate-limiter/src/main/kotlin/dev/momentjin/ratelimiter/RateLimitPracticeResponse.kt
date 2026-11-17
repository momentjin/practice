package dev.momentjin.ratelimiter

/** 미구현 전략은 implemented=false, 구현된 전략은 허용 여부와 잔량을 반환한다. */
data class RateLimitPracticeResponse(
    val strategy: String,
    val clientId: String,
    val implemented: Boolean = false,
    val allowed: Boolean? = null,
    val remainingTokens: Long? = null,
    val retryAfterMs: Long? = null,
    val message: String = "서비스 뼈대만 준비되었습니다. 다음 단계에서 요청 제한을 구현합니다.",
)
