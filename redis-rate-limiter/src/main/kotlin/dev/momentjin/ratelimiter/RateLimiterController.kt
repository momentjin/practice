package dev.momentjin.ratelimiter

import dev.momentjin.ratelimiter.service.Bucket4jRateLimiterService
import dev.momentjin.ratelimiter.service.SlidingWindowCounterRateLimiterService
import dev.momentjin.ratelimiter.service.SlidingWindowLogRateLimiterService
import dev.momentjin.ratelimiter.service.TokenBucketRateLimiterService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/rate-limit")
class RateLimiterController(
    private val slidingWindowLogService: SlidingWindowLogRateLimiterService,
    private val slidingWindowCounterService: SlidingWindowCounterRateLimiterService,
    private val tokenBucketService: TokenBucketRateLimiterService,
    private val bucket4jService: Bucket4jRateLimiterService,
) {
    @PostMapping("/token-bucket")
    fun tokenBucket(
        @RequestHeader("X-Client-Id", defaultValue = "demo") clientId: String,
    ): ResponseEntity<RateLimitPracticeResponse> {
        val result = tokenBucketService.tryAcquire(clientId)
        val status = if (result.allowed == true) HttpStatus.OK else HttpStatus.TOO_MANY_REQUESTS
        return ResponseEntity.status(status).body(result)
    }

    @PostMapping("/sliding-window-log")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    fun slidingWindowLog(
        @RequestHeader("X-Client-Id", defaultValue = "demo") clientId: String,
    ): RateLimitPracticeResponse = slidingWindowLogService.tryAcquire(clientId)

    @PostMapping("/sliding-window-counter")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    fun slidingWindowCounter(
        @RequestHeader("X-Client-Id", defaultValue = "demo") clientId: String,
    ): RateLimitPracticeResponse = slidingWindowCounterService.tryAcquire(clientId)


    @PostMapping("/bucket4j")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    fun bucket4j(
        @RequestHeader("X-Client-Id", defaultValue = "demo") clientId: String,
    ): RateLimitPracticeResponse = bucket4jService.tryAcquire(clientId)
}
