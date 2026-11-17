# Redis Rate Limiter 연습

Kotlin + Spring Boot MVC + Redis 기반 시스템 디자인 실습 프로젝트입니다.
토큰 버킷은 Redis + Lua로 구현했습니다. 나머지 세 전략은 실습용 뼈대입니다.

## 구성

- Kotlin 2.4.21 / Spring Boot 4.1.2 / Java 17
- Spring MVC: HTTP API
- Spring Data Redis: Lettuce 기반 Redis 연결, StringRedisTemplate 자동 설정
- Actuator: 애플리케이션 및 Redis 연결 상태 확인
- Docker Compose: 로컬 실습용 Redis 7.4 (데이터 영속화 없음)

## 실행

이 디렉토리에서 실행합니다. JDK 17과 Docker가 필요합니다.

```bash
docker compose up -d
./gradlew bootRun
```

기본 Java 버전이 다르면 macOS에서는 다음처럼 JDK 17을 지정할 수 있습니다.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun
```

## 확인

```bash
curl -i -X POST http://localhost:8080/api/rate-limit/token-bucket \
  -H "X-Client-Id: user-1"
curl http://localhost:8080/actuator/health
docker compose exec redis redis-cli ping
```

- `/api/rate-limit/token-bucket`: 허용 시 HTTP 200, 토큰 부족 시 HTTP 429
- `/actuator/health`: Redis까지 정상 연결되면 `{"status":"UP"}`. Redis가 내려가면 DOWN 및 HTTP 503 반환
- `redis-cli ping`: `PONG` 반환

## 테스트와 종료

```bash
./gradlew test
# Redis 실행 중에 충전·사용자별 분리·동시 요청 검증
./gradlew redisIntegrationTest
docker compose down
```

애플리케이션은 실행 터미널에서 Ctrl+C로 종료합니다.
기본 컨텍스트 테스트에는 실행 중인 Redis가 필요하지 않습니다.

## 환경 변수

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| SERVER_PORT | 8080 | 애플리케이션 포트 |
| REDIS_HOST | localhost | Redis 주소 |
| REDIS_PORT | 6379 | Redis 포트 및 Compose 호스트 포트 |

6379가 사용 중이면 `REDIS_PORT=6380 docker compose up -d`로 Redis를 띄우고,
`REDIS_PORT=6380 ./gradlew bootRun`으로 앱에도 같은 포트를 지정하세요.

## 다음 실습

1. 토큰 버킷 직접 구현 (Redis + Lua)
2. 슬라이딩 윈도우 로그 구현 (Sorted Set + Lua)
3. 슬라이딩 윈도우 카운터 구현 (가중 합산)
4. Bucket4j + Redis로 토큰 버킷 구현 및 직접 구현과 비교

## 전략별 실습 API

모두 `POST`이며 `X-Client-Id` 헤더로 사용자를 구분합니다 (생략 시 `demo`).
학습용 식별자로, 실제 인증된 사용자 ID를 검증하는 기능은 아직 없습니다.
컨트롤러와 응답 모델은 기본 패키지 `dev.momentjin.ratelimiter`에,
전략별 서비스는 `dev.momentjin.ratelimiter.service` 패키지에 있습니다.
하나의 `RateLimiterController`에서 전략별 Service를 호출합니다.

| 경로 | 전략 | 구현할 내용 |
| --- | --- | --- |
| `/api/rate-limit/sliding-window-log` | 슬라이딩 윈도우 로그 | Sorted Set + Lua, 요청별 정확한 기록 |
| `/api/rate-limit/sliding-window-counter` | 슬라이딩 윈도우 카운터 | 이전·현재 구간 카운터의 가중 합산, 근사치 |
| `/api/rate-limit/token-bucket` | 토큰 버킷 직접 구현 | 토큰 잔량 / 충전 시각 + Lua |
| `/api/rate-limit/bucket4j` | Bucket4j + Redis | 라이브러리를 이용한 분산 토큰 버킷 |

토큰 버킷은 최대 10개, 초당 1개 충전, 요청당 1개 소비합니다.
HTTP 200 / 429와 `allowed`, `remainingTokens`(정수 내림)를 반환합니다.
판단과 상태 저장은 Lua 한 번으로 처리하며, 소수 토큰은 Redis에 보존합니다.
유휴 버킷은 1시간 TTL 이후 정리됩니다.
Redis 연결 실패는 요청을 허용하지 않고 서버 오류로 처리합니다.

나머지 세 경로는 HTTP 501과 `implemented: false`를 반환합니다.
요청 제한 기능이 동작하는 것으로 오해하지 않도록 허용 응답을 반환하지 않습니다.
Service의 TODO를 한 전략씩 구현하면서 HTTP 200 / 429 응답 및 대기 시간 처리를 추가합니다.

Bucket4j 경로도 현재 뼈대만 있으며 라이브러리 의존성은 아직 추가하지 않았습니다.
해당 실습 단계에서 Redis 연동 모듈과 버킷 정책을 함께 설정합니다.

## Gateway와 비교 실습

`GET /api/practice/ping`은 Gateway가 전달할 관찰용 백엔드 API입니다.
이 API에는 자체 레이트 리미터가 없으므로 Gateway의 제한만 확인할 수 있습니다.
[Gateway 두 버전 실행 가이드](../GATEWAY_PRACTICE.md)를 참고하세요.
