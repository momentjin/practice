package dev.momentjin.ratelimiter

import dev.momentjin.ratelimiter.service.TokenBucketRateLimiterService
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** docker compose up -d 이후 ./gradlew redisIntegrationTest로 실행한다. */
@Tag("redis")
@SpringBootTest
class TokenBucketIntegrationTests {
    @Autowired private lateinit var service: TokenBucketRateLimiterService
    @Autowired private lateinit var redis: StringRedisTemplate
    private val clients = mutableListOf<String>()

    private fun client(): String = "test-${UUID.randomUUID()}".also { clients.add(it) }
    private fun key(client: String) = "rate-limit:token-bucket:$client"

    @AfterEach
    fun cleanup() {
        redis.delete(clients.map(::key))
    }

    @Test
    fun `처음 10회 허용 후 거절하고 다른 사용자는 별도로 허용한다`() {
        val client = client()
        // 첫 요청은 가득 찬 버킷(10개)으로 시작한다.
        repeat(10) { assertEquals(true, service.tryAcquire(client).allowed) }
        assertEquals(false, service.tryAcquire(client).allowed)
        assertEquals(true, service.tryAcquire(client()).allowed)
        assertTrue(redis.getExpire(key(client)) > 0)
    }

    @Test
    fun `오래 쉰 버킷은 최대 용량까지만 충전한다`() {
        val client = client()
        redis.opsForHash<String, String>().putAll(key(client), mapOf(
            "tokens" to "0", "lastRefillAt" to "0",
        ))
        val result = service.tryAcquire(client)
        assertEquals(true, result.allowed)
        assertEquals(9L, result.remainingTokens)
    }

    @Test
    fun `소수 토큰은 보존되고 시간이 지나면 다시 허용한다`() {
        val client = client()
        // 0.5개 상태에서 거절돼도 0.5개(+그새 충전된 몫)는 저장돼 남는다.
        redis.opsForHash<String, String>().putAll(key(client), mapOf(
            "tokens" to "0.5", "lastRefillAt" to (System.currentTimeMillis() + 60_000).toString(),
        ))
        assertEquals(false, service.tryAcquire(client).allowed)
        assertTrue(redis.opsForHash<String, String>().get(key(client), "tokens")!!.toDouble() >= 0.5)
        // 0.5개 + 약 0.5초 충전 → 1개가 되어 다시 허용된다.
        Thread.sleep(600)
        assertEquals(true, service.tryAcquire(client).allowed)
    }

    @Test
    fun `동시에 50회 요청해도 토큰 10개만 소비한다`() {
        val client = client()
        val executor = Executors.newFixedThreadPool(16)
        try {
            val results = executor.invokeAll((1..50).map {
                Callable { service.tryAcquire(client).allowed }
            }).map { it.get() }
            assertEquals(10, results.count { it == true })
            assertEquals(40, results.count { it == false })
            // 요청 사이에 아주 조금씩 충전되므로 정확히 0이 아니라 1개 미만인지 확인한다.
            assertTrue(redis.opsForHash<String, String>().get(key(client), "tokens")!!.toDouble() < 1.0)
        } finally {
            executor.shutdownNow()
        }
    }
}
