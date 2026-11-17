package dev.momentjin.gateway.mvc

import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri
import org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse

@Configuration
class GatewayRoutes {
    @Bean
    fun practiceRoute(
        @Value("\${practice.backend-uri}") backendUri: String,
    ): RouterFunction<ServerResponse> = route("practice-mvc")
        .GET("/api/practice/**", http())
        .before(uri(backendUri))
        .filter(rateLimit { config ->
            // 10개를 10초에 걸쳐 연속 충전: 최대 10개, 평균 초당 1개.
            config.setCapacity(10)
                .setPeriod(Duration.ofSeconds(10))
                .setTokens(1)
                .setTimeout(Duration.ofSeconds(2))
                .setKeyResolver { request ->
                    val clientId = request.headers().firstHeader("X-Client-Id")
                        ?.trim()?.takeIf { it.isNotEmpty() }
                    // 빈 키는 Gateway 기본 동작으로 403. 사용자별 버킷을 구분한다.
                    clientId?.let { "gateway:mvc:$it" } ?: ""
                }
        })
        .build()
}
