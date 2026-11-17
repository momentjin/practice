# Gateway WEBFLUX 실습

Kotlin · Spring Boot 4.1.2 · Spring Cloud 2025.1.4 기반 독립 프로젝트입니다.

- 기본 포트: 8081
- RequestRateLimiter + 내장 RedisRateLimiter
- 최대 10개 / 초당 1개 충전 / 요청당 1개 소비
- `GET /api/practice/ping`을 기존 MVC 백엔드(8080)로 전달
- `X-Client-Id` 헤더 필수, 빈 값은 403, 토큰 부족은 429

[전체 실행 순서·설정·검증 방법](../GATEWAY_PRACTICE.md)을 참고하세요.

Redis와 백엔드를 먼저 실행한 후 이 디렉토리에서 실행합니다.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun
```
