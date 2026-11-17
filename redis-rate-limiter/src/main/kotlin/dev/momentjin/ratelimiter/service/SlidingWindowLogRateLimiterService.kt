package dev.momentjin.ratelimiter.service

import dev.momentjin.ratelimiter.RateLimitPracticeResponse
import org.springframework.stereotype.Service

@Service
class SlidingWindowLogRateLimiterService {
    fun tryAcquire(clientId: String): RateLimitPracticeResponse {
        // TODO: Redis Sorted Set에 요청별 시각을 기록하고 오래된 기록을 제거.
        return RateLimitPracticeResponse(strategy = "sliding-window-log", clientId = clientId)
    }
}
