package dev.momentjin.ratelimiter.service

import dev.momentjin.ratelimiter.RateLimitPracticeResponse
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service

@Service
class TokenBucketRateLimiterService(
    private val redisTemplate: StringRedisTemplate,
) {
    // 매 요청마다 파일을 읽거나 스크립트 객체를 새로 만들지 않는다.
    private val tokenBucketScript = DefaultRedisScript<List<*>>().apply {
        setLocation(ClassPathResource("scripts/token-bucket.lua"))
        resultType = List::class.java
    }

    /** 최대 10개, 초당 1개 충전, 요청마다 1개 소비. 실제 판단과 갱신은 Lua에서 한다. */
    fun tryAcquire(clientId: String): RateLimitPracticeResponse {
        val result = checkNotNull(
            redisTemplate.execute(
                tokenBucketScript,
                listOf("rate-limit:token-bucket:$clientId"),
                "10", // ARGV[1]: 최대 토큰 수
                "1",  // ARGV[2]: 초당 충전량
            )
        ) { "토큰 버킷 스크립트의 반환값이 없습니다." }

        val allowed = (result[0] as Number).toLong() == 1L
        return RateLimitPracticeResponse(
            strategy = "token-bucket",
            clientId = clientId,
            implemented = true,
            allowed = allowed,
            remainingTokens = (result[1] as Number).toLong(),
            message = if (allowed) "요청을 허용했습니다." else "토큰이 부족합니다. 잠시 후 다시 요청하세요.",
        )
    }
}
