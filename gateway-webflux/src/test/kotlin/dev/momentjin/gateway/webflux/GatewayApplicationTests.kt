package dev.momentjin.gateway.webflux

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

// MVC 버전은 Redis 버킷 연결을 생성하므로 Redis가 실행 중이어야 한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTests {
    @Test
    fun contextLoads() {
    }
}
