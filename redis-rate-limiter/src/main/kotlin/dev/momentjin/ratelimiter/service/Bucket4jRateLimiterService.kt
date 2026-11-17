package dev.momentjin.ratelimiter.service

import dev.momentjin.ratelimiter.RateLimitPracticeResponse
import org.springframework.stereotype.Service

@Service
class Bucket4jRateLimiterService {
    fun tryAcquire(clientId: String): RateLimitPracticeResponse {
        // TODO: Bucket4j의 Redis ProxyManager를 연결하고 사용자별 버킷에서 토큰을 소비.
        return RateLimitPracticeResponse(strategy = "bucket4j", clientId = clientId)
    }
}
