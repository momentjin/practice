package dev.momentjin.ratelimiter.service

import dev.momentjin.ratelimiter.RateLimitPracticeResponse
import org.springframework.stereotype.Service

@Service
class SlidingWindowCounterRateLimiterService {
    fun tryAcquire(clientId: String): RateLimitPracticeResponse {
        // TODO: 이전 구간과 현재 구간의 카운트를 시간 비율로 가중 합산.
        return RateLimitPracticeResponse(strategy = "sliding-window-counter", clientId = clientId)
    }
}
