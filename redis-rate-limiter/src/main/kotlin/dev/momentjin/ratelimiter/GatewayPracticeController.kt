package dev.momentjin.ratelimiter

import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class GatewayPracticeController {
    @GetMapping("/api/practice/ping")
    fun ping(
        @RequestHeader("X-Client-Id", defaultValue = "demo") clientId: String,
    ): Map<String, String> = mapOf(
        "message" to "백엔드에 도착했습니다.",
        "clientId" to clientId,
        "requestId" to UUID.randomUUID().toString(),
    )
}
