# Gateway 레이트 리미터 실습

직접 만든 Lua 버전과, Spring Cloud Gateway가 제공하는 토큰 버킷을 비교합니다.
세 앱은 독립적인 Gradle 프로젝트이며 동시에 실행할 수 있습니다.

```text
                        Redis :6379
                        ↕         ↕
요청 → WebFlux Gateway :8081 ──┐
                             ├→ MVC 백엔드 :8080 → 응답
요청 → MVC Gateway     :8082 ──┘
```

## 프로젝트별 역할

| 프로젝트 | 역할 | 읽어볼 파일 |
| --- | --- | --- |
| `redis-rate-limiter` | 기존 MVC 앱, 직접 구현 실습 및 Gateway의 백엔드 | `GatewayPracticeController.kt`, `scripts/token-bucket.lua` |
| `gateway-webflux` | 내장 `RequestRateLimiter` + `RedisRateLimiter` | `application.yaml`, `RateLimiterConfiguration.kt` |
| `gateway-mvc` | 내장 Bucket4j `rateLimit` 필터 + Redis | `GatewayRoutes.kt`, `RedisBucketConfiguration.kt` |

공통 정책은 최대 10개, 평균 초당 1개 충전, 요청당 1개 소비입니다.
WebFlux 내장 구현은 초 단위로 계산하고, Bucket4j는 더 세밀하게 계산하므로 시간 경계 응답은 완전히 같지 않을 수 있습니다.
MVC의 `capacity=10`, `period=10초`는 10개를 10초에 걸쳐 연속 충전하는 뜻이며, 10초마다 한꺼번에 충전하는 설정이 아닙니다.
두 버전 모두 별도 충전 스케줄러를 만들지 않습니다.

- `GET /api/practice/ping` 요청만 Gateway 실습 대상으로 사용합니다.
- 백엔드의 이 API에는 자체 레이트 리미터가 없습니다. 중복 제한 없이 Gateway를 관찰하기 위한 API입니다.
- 기존 `POST /api/rate-limit/token-bucket`은 백엔드에 직접 요청해 Lua 구현을 실습합니다.
- Gateway 사용자를 구분하는 `X-Client-Id`가 없거나 공백이면 HTTP 403, 토큰이 부족하면 HTTP 429입니다.
- 성공 응답에는 백엔드의 `requestId`와 Gateway가 추가하는 `X-RateLimit-Remaining` 헤더가 있습니다.
- `X-Client-Id`는 학습용 식별자입니다. 운영에서는 인증된 사용자 정보 등으로 키를 결정하세요.

## 실행 순서

JDK 17과 Docker가 필요합니다. 아래 경로는 `practice` 디렉토리 기준입니다.
각 앱은 별도 터미널에서 실행하고, 종료는 Ctrl+C로 합니다.

### 1. Redis

```bash
cd redis-rate-limiter
docker compose up -d --wait
```

### 2. 백엔드 (8080)

```bash
cd redis-rate-limiter
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun
```

### 3. WebFlux Gateway (8081)

```bash
cd gateway-webflux
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun
```

### 4. MVC Gateway (8082)

```bash
cd gateway-mvc
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun
```

각 `cd`는 새 터미널에서 `practice`를 기준으로 실행합니다.
IntelliJ에서는 각 프로젝트의 `GatewayApplication.kt`를 실행해도 됩니다.
두 클래스 이름이 같으므로 패키지의 `webflux`, `mvc`를 확인하세요.

## 요청 보내기

```bash
# 백엔드 직접 접근: Gateway 제한을 거치지 않음
curl -i http://localhost:8080/api/practice/ping -H 'X-Client-Id: user-1'

# WebFlux Gateway 경유
curl -i http://localhost:8081/api/practice/ping -H 'X-Client-Id: user-1'

# MVC Gateway 경유
curl -i http://localhost:8082/api/practice/ping -H 'X-Client-Id: user-1'
```

반복 요청으로 200과 429를 비교하세요. 포트를 8082로 바꾸면 MVC 실습입니다.

```bash
for i in {1..15}; do
  curl -s -o /dev/null -w '%{http_code}\n' \
    http://localhost:8081/api/practice/ping -H 'X-Client-Id: user-1'
done
```

처음 최대 10회 통과 후 제한됩니다. 실행 중 충전된 토큰이 있으면 더 통과할 수 있습니다.
1초 이상 기다리면 다시 요청할 수 있고, 사용자 ID를 바꾸면 별도 버킷으로 처리됩니다.
429 본문과 헤더는 프레임워크 기본 형식을 사용하므로 직접 구현한 JSON 응답과 다릅니다.

## 자동 검증

세 앱과 Redis를 실행한 상태에서 `practice`에서 실행합니다. Python 3 표준 라이브러리만 사용합니다.

```bash
python3 scripts/verify_gateway_rate_limit.py
```

순차·동시 요청의 한도, 재충전, 사용자 분리, 누락된 사용자 키 차단 및 두 Gateway 간 버킷 분리를 검사합니다.
각 Gateway 프로젝트의 `./gradlew test bootJar`로 앱 구성과 빌드도 확인할 수 있습니다.
MVC 테스트는 실제 Redis 연결을 생성하므로 Redis가 실행 중이어야 합니다.

## Redis 관찰

`redis-rate-limiter` 디렉토리에서 실행합니다.

```bash
docker compose exec redis redis-cli --scan --pattern '*gateway*'
```

WebFlux와 MVC는 다른 키를 씁니다. 같은 사용자 ID라도 버킷을 공유하지 않습니다.
WebFlux는 내장 Redis 스크립트의 키 형식을, MVC는 `gateway:mvc:<사용자>` 키에 Bucket4j의 바이너리 상태를 사용합니다.
따라서 직접 구현에서 쓴 `HMGET`으로 Gateway 버킷을 읽을 수는 없습니다.
만료 정책이 있어 유휴 버킷 키는 시간이 지나면 사라집니다.

동일한 종류의 Gateway를 다른 포트로 하나 더 띄우면 Redis를 통한 버킷 공유도 실습할 수 있습니다.

```bash
# gateway-webflux 디렉토리에서
SERVER_PORT=8083 JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun
```

8081과 8083에 같은 사용자 ID로 번갈아 요청하면 두 서버가 같은 버킷을 소비합니다.

## 환경 변수와 종료

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `SERVER_PORT` | WebFlux 8081 / MVC 8082 | 각 Gateway의 포트 |
| `BACKEND_URI` | `http://localhost:8080` | 전달할 백엔드 주소 |
| `REDIS_HOST` | `localhost` | 공유 Redis 주소 |
| `REDIS_PORT` | `6379` | 공유 Redis 포트 |

Redis 포트를 바꾸면 세 앱에 모두 같은 값을 지정하세요.
MVC는 시작 시 Redis 연결이 필요합니다.
앱을 Ctrl+C로 종료한 다음 Redis를 정리합니다.

```bash
cd redis-rate-limiter
docker compose down
```

## 참고

- [WebFlux RequestRateLimiter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/requestratelimiter-factory.html)
- [MVC RateLimiter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/filters/ratelimiter.html)
- [Spring Boot와 Spring Cloud 호환 버전](https://spring.io/projects/spring-cloud)
