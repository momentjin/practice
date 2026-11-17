package dev.momentjin.gateway.webflux

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Mono

@Configuration
class RateLimiterConfiguration {
    @Bean
    fun clientKeyResolver(): KeyResolver = KeyResolver { exchange ->
        val clientId = exchange.request.headers.getFirst("X-Client-Id")
            ?.trim()?.takeIf { it.isNotEmpty() }
        // 키가 없으면 Mono.empty(): Gateway 기본 정책에 따라 403으로 거절한다.
        // 학습용 헤더이며, 운영에서는 인증된 사용자 ID 등을 사용한다.
        Mono.justOrEmpty(clientId?.let { "gateway:webflux:$it" })
    }
}
