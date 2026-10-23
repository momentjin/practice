# batch-practice

Kotlin · Spring Boot · Spring Batch · JPA · MySQL로 배치 개념을 학습한다.
기본 패키지는 기존 표기인 `dev.momentjin.batchpracitce`를 유지한다.

## 구조

```text
src/main/kotlin/dev/momentjin/batchpracitce/
├── BatchPracticeApplication.kt
├── domain/                         # 배치와 독립된 업무 기반
│   ├── merchant/                   # 가맹점 엔티티·저장소·등록 서비스
│   ├── order/                      # 주문 엔티티·저장소·상태 전이 서비스
│   ├── ledger/                     # 결제·취소 원장 엔티티·저장소
│   └── settlement/                 # 정산 결과·저장소·수수료 계산 정책
└── practice/
    ├── common/
    │   ├── config/                 # JSON ExecutionContext, 기존 Base64 읽기 호환
    │   ├── fixture/                # 반복 가능한 실습 데이터, 100만 건 시드
    │   ├── launch/                 # CLI → 명시적 JobParameters → 실행
    │   ├── reader/                 # 실습 데이터 조회용 StepScope Reader
    │   ├── settlement/             # 상태 없는 계산·로그 Writer·멱등 DB Writer
    │   ├── step/                   # 입력 준비와 트랜잭션 연결
    │   └── concurrency/            # 스레드풀 제출 대기 구현
    ├── practice00/                 # Job → Step → Tasklet
    ├── practice01/                 # Chunk, pageSize, maxItemCount
    ├── practice02/                 # 파라미터 타입·식별 정책·늦은 주입
    ├── practice03/                 # 청크 롤백·체크포인트·재시작
    ├── practice04/                 # 업무 데이터 멱등성
    ├── practice05/                 # Retry · Skip · Filter
    ├── practice06/                 # 커밋 기준 진행률과 업무 집계
    ├── practice07/                 # 파티셔닝과 스레드풀
    ├── practice08/                 # 제출 대기와 백프레셔
    └── practice10/                 # 결과 검증과 Job 간 의존 실행
```

기존 과제 번호를 유지했다. 첫 Tasklet은 00으로 두었으며, 제외하기로 한 스케줄러 과제 09는 없다.
예전 `hello/chunk/settlement/restart/partition/benchmark/exerciseXX` 프로필은 번호별 `practiceXX`로 정리했다.
성능 비교용 benchmark와 과거 중복 구현은 제거했다.

## 환경

- JDK 17 / Kotlin 2.4.21 / Spring Boot 4.1.2 / Spring Batch 6.0.6
- Gradle Wrapper / 로컬 MySQL 8.4
- DB 기본 주소: `127.0.0.1:3306/batch_practice`
- Flyway로 스키마 관리, Hibernate `ddl-auto=validate`
- 웹 서버 없이 CLI로 실행 후 종료

```sh
mysql -u root -p -e 'CREATE DATABASE IF NOT EXISTS batch_practice CHARACTER SET utf8mb4;'
cp .env.example .env
# .env에 본인의 DB 계정과 비밀번호 설정
```

기존 `.env`가 있다면 복사하지 않고 그대로 사용한다. `.env`는 Git에서 제외한다.
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE` 환경변수도 사용할 수 있다.

```sh
# 일반 실행: 마이그레이션·매핑 확인 후 종료
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun

# 도메인·공통 기반 테스트 및 빌드: 별도 H2 DB 사용
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew clean build
```

기존 V1~V3 migration은 유지했다. 로컬 주문·원장·정산·배치 이력을 삭제하는 작업은 하지 않는다.
기존 이력과 JSON/Base64 Context는 남아 있지만, 새 실습은 아래의 새 파라미터 규칙과 Reader 이름을 사용한다.
구조 개편 이전에 실패한 실행을 새 코드로 재시작하는 용도로는 사용하지 않는다.

## 선택: 대용량 주문 데이터 준비

```sh
# 02에서 조회할 가맹점 데이터가 없다면 작은 수부터 준비
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=seed --seed.count=1000'

# 누적 목표 100만 건까지 생성
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=seed --seed.count=1000000'
```

시드는 가맹점 100곳과 CREATED/PAID/CANCELLED 주문 및 결제·취소 원장을 준비한다.
고정 주문 번호로 기존 데이터를 재사용하고, 1,000건 단위 JDBC 트랜잭션으로 생성한다.
같은 MySQL 시더의 동시 실행은 advisory lock으로 막는다. IDENTITY 기반 JPA INSERT를 대량 저장하는 코드와 구분한다.
번호별 실습의 소량 입력은 별도 dataset으로 자동 준비하므로, 02를 제외하면 시드 없이도 실행할 수 있다.

## 실행 파라미터와 입력 데이터

02를 제외한 모든 실습은 다음 경로로 실행한다.

```text
이름=값 CLI 인자
→ PracticeParameters.parse()
→ toJobParameters(): 타입과 identifying 여부 지정
→ PracticeJobRunner 또는 SettlementWorkflowRunner
→ JobOperator.start()
→ Job validator
```

| 파라미터 | 타입 | 식별 여부 | 용도 |
| --- | --- | --- | --- |
| `dataset` | String | true | 실습 입력 데이터 집합 |
| `run.id` | Long | true | 같은 데이터로 새 JobInstance를 만드는 회차 |
| `requestedBy` | String | false | 실행 요청자, 생략하면 `local` |

`JobInstance = Job 이름 + 식별 파라미터`다. 실패 재시작에서는 dataset/run.id를 모두 유지한다.
완료된 동일 JobInstance는 다시 실행할 수 없다. requestedBy만 바꾸어도 새 인스턴스가 되지 않는다.

실습 01·03·04·05·06·07·10은 준비 Step에서 입력을 만든다.
주문 번호 `practice-{dataset}-00001` 형식으로 조회 범위를 고정하고, 이미 존재하는 주문은 검증 후 재사용한다.
다른 dataset의 주문이나 기존 대용량 시드를 처리하지 않는다. 의도적인 빈 입력 조회도 없다.
새 실습의 저장 건수를 처음부터 관찰하려면 **새 dataset**을 사용한다. run.id만 바꾸면 같은 주문을 다시 처리한다.
금액은 원 단위 Long, 수수료는 주문 금액의 3%에서 원 미만 버림이다.

한 번에 하나의 실습 프로필만 사용한다. Spring 옵션은 `--`, Job 파라미터는 `이름=값`으로 전달한다.
실패한 Job은 Runner도 실패로 종료해 CLI 종료 코드에 반영한다.

## 실습별 실행과 기대 결과

아래 명령은 `batch-practice` 디렉터리에서 실행한다.

### 00 — Job / Step / Tasklet

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice00 dataset=hello run.id=1'
```

`helloBatchJob`이 로그를 출력하고 Job/Step 메타데이터를 남긴다.

### 01 — Chunk / pageSize / maxItemCount

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice01 dataset=chunk run.id=1'
```

`paidOrderPreviewJob`: 주문 50건 준비 → 앞의 25건만 읽기 → 계산 결과 로그 출력.
pageSize=7, chunkSize=10이며 마지막 청크는 5건이다. 정산 결과는 DB에 저장하지 않는다.

### 02 — 명시적인 식별 파라미터와 StepScope

```sql
-- 사용할 실제 가맹점 PK를 확인한다. 가맹점 code와 다른 값이다.
SELECT m.id, m.code, COUNT(*) AS paid_orders
FROM merchants m JOIN purchase_orders o ON o.merchant_id = m.id
WHERE o.status = 'PAID' GROUP BY m.id, m.code ORDER BY m.id;
```

```sh
# merchant.id=1은 위에서 확인한 실제 ID로 바꾼다.
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice02 merchant.id=1 requestedBy=momentjin'
```

`merchantOrderPreviewJob`은 해당 가맹점의 PAID 주문 최대 25건을 계산하고 로그만 출력한다.
이 과제는 사용자가 선택한 가맹점을 조회하므로 자동 입력 준비 Step이 없다.
`merchant.id`는 Long/identifying=true, `requestedBy`는 String/identifying=false다.
`MerchantOrderPreviewJobParametersSpec.toJobParameters()`가 실제 Runner의 실행 경로에서 호출된다.
Reader만 StepScope로 만들고, Job/Step 구성 자체는 일반 Bean으로 유지한다.

### 03 — 청크 롤백과 같은 JobInstance 재시작

```sh
# 새 dataset 사용. 세 번째 청크의 flush 뒤 커밋 전 실패 → 결과 20건
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice03 dataset=restart run.id=1 --practice.force-fail=true'

# 같은 식별 값으로 재시작 → 준비 Step 건너뜀, 남은 30건 저장, 결과 총 50건
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice03 dataset=restart run.id=1 --practice.force-fail=false'
```

`restartPracticeJob`의 JobInstance는 같고 JobExecution은 추가된다.
실패 후 Reader 체크포인트는 20, 재시작 후 50이다. force-fail은 Spring 설정으로 식별에 포함하지 않는다.
Writer 카운터는 StepScope여서 같은 JVM의 다음 실행에도 이전 실행 값이 남지 않는다.

### 04 — 다른 JobInstance에서 같은 주문 재처리

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice04 dataset=idempotent run.id=1'
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice04 dataset=idempotent run.id=2'
```

`idempotentSettlementJob`: 첫 실행 신규 50건, 두 번째 실행 신규 0건/기존 50건.
결과는 총 50건이다. 기존 결과의 주문 금액·수수료·지급액이 다르면 덮어쓰지 않고 실패한다.
DB의 `order_id UNIQUE`도 중복 저장을 막는다. 두 실행의 WRITE_COUNT는 모두 50일 수 있다.
동시 실행에서 조회와 INSERT 사이에 경쟁하면 UNIQUE 충돌로 한 실행이 실패할 수 있다.
중복 행이 생기지 않는 것과 모든 동시 요청이 성공하는 것은 구분한다.

### 05 — Retry / Skip / Filter

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice05 dataset=faults run.id=1'
```

`faultTolerantSettlementJob`의 기본 동작:

- 고정 순번 5번 주문: 처음 두 번 일시 오류, 세 번째 성공. retryLimit=2.
- 13번 주문: 데이터 오류로 Skip. Step 전체 skipLimit=2.
- 21번 주문: Processor가 null을 반환해 Filter.
- READ_COUNT=50, WRITE_COUNT=48, PROCESS_SKIP_COUNT=1, FILTER_COUNT=1, 최종 COMPLETED.

순번은 ID나 Processor 호출 횟수가 아니라 준비한 주문 번호에서 얻는다. 재시도에도 대상이 바뀌지 않는다.
제외된 주문의 기존 정산 결과가 있었다면 삭제하지 않는다. 새 dataset에서 결과 행 수를 확인한다.

한도 초과를 확인할 때는 각각 새 dataset과 옵션을 사용한다.

```sh
# 5번 주문이 세 번 실패하여 Retry 한도 초과
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice05 dataset=retry-limit run.id=1 --practice.transient-failures=3'

# Skip 대상 3건으로 Step 전체 한도 2건 초과
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice05 dataset=skip-limit run.id=1 --practice.invalid-positions=13,14,15'
```

추가 옵션 `--practice.filter-position=0`은 Filter를 끈다.

### 06 — 커밋 기준 진행률과 업무 집계

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice06 dataset=progress run.id=1'
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice06 dataset=progress run.id=2'
```

`progressPracticeJob`은 `settlement_execution_summary`에 전체 대상·재시작 위치·신규·기존·지급액을 기록한다.
업무 집계 UPDATE는 정산 저장과 같은 트랜잭션에 참여하고, 진행 로그는 afterCommit에서 출력한다.
첫 실행 신규 50건, 두 번째 실행 기존 50건이며 완료 건수와 실제 신규 저장 수를 구분한다.
이 과제에는 Skip/Filter를 섞지 않는다. 필요하면 03과 같은 `--practice.force-fail=true/false`로 집계 롤백을 확인한다.

### 07 — 파티셔닝과 스레드풀

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice07 dataset=partition run.id=1'
```

```text
partitionPracticeJob
├── 입력 준비: 80건
└── Manager Step
    ├── Partitioner: 겹치지 않는 범위 8개, 각 10건
    └── 스레드풀: core=4, max=4, queue=4
        └── Worker Step × 8
            ├── StepScope Reader: 자기 범위만 조회
            ├── 공용 Processor
            └── Writer: chunkSize=5로 저장
```

gridSize는 분할 힌트, 실제 분할은 Partitioner가 반환한 Map으로 결정된다.
스레드 수는 동시 실행 수, Chunk 크기는 Worker 안의 트랜잭션 단위다.
총 결과 80건이며 Worker별 범위와 실행 스레드를 로그·Step Context에서 확인한다.

### 08 — 제출 대기와 백프레셔

```sh
# 기본: Spring 제공 SimpleAsyncTaskExecutor, concurrencyLimit=4
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice08 dataset=backpressure run.id=1'

# 비교: 고정 스레드풀 4개 + 큐 2칸 + Semaphore 제출 제어
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice08 dataset=backpressure run.id=2 --practice.submission-mode=pool'
```

`backpressurePracticeJob`은 느린 작업을 흉내 내는 Tasklet 파티션 10개를 모두 처리한다.
주문/정산 데이터를 쓰지 않고 Batch 메타데이터만 남긴다.

- framework: 내부 작업 큐 대신 Manager의 execute() 호출이 블로킹된다. 작업마다 새 스레드를 만들지만 동시 실행은 4개로 제한한다.
- pool: 스레드를 재사용하고 실행 4개+대기 2개의 미완료 작업을 허가증 6개로 제한한다.
- pool의 허가증 반환 직후 실제 큐 여유가 생기기 전의 짧은 거절 경계는 10ms 대기 후 재제출한다.
- 대기 중 인터럽트와 Executor 종료를 확인하며, 제출 실패와 작업 종료에서 허가증을 반납한다.
- 풀은 반드시 이 제어기를 통해서만 사용하고 AbortPolicy를 유지한다. 범용 외부 작업 큐 구현은 아니다.

`--practice.practice08.work-millis=2000`, `--practice.practice08.queue-capacity=2`로 비교 설정을 바꿀 수 있다.
framework 모드에서는 queue-capacity가 동시 실행 제한에 영향을 주지 않는다.

### 10 — 검증 Step과 별도 Job 연결

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun --args='--spring.profiles.active=practice10 dataset=workflow run.id=1'
```

```text
SettlementWorkflowRunner
├── verifiedSettlementJob
│   ├── 입력 준비 Step
│   ├── 정산 Step
│   └── 검증 Step: 대상·누락·중복·개별 금액·합계 확인
└── 정산 Job이 COMPLETED일 때만 payoutPreviewJob 실행
    └── 선행 완료 재확인 + 데이터 재검증 + 지급액 로그
```

실제 송금은 하지 않는다. 지급 Step Context에 `upstream.execution.id`를 남긴다.
검증이 실패하면 정산 Job은 FAILED지만 앞서 커밋한 결과는 남는다. 지급 Job은 시작하지 않는다.
같은 식별 값으로 재시작하면 완료된 준비/정산 Step을 건너뛰고 검증 Step부터 다시 실행한다.
지급만 실패했다면 Runner는 완료된 정산 Job을 재사용하고 지급 Job만 재시작한다.

검증 실패와 차단을 재현하려면 새 dataset에 `--practice.validation.expected-count=51`을 지정한다.
이후 같은 dataset/run.id에서 해당 옵션을 제거하면 기본 50건 기준으로 검증을 재시도한다.

## 메타데이터와 쿼리 확인

```sql
SELECT i.JOB_NAME, i.JOB_INSTANCE_ID, e.JOB_EXECUTION_ID, e.STATUS
FROM BATCH_JOB_INSTANCE i JOIN BATCH_JOB_EXECUTION e USING (JOB_INSTANCE_ID)
ORDER BY e.JOB_EXECUTION_ID DESC;

SELECT JOB_EXECUTION_ID, PARAMETER_NAME, PARAMETER_TYPE, PARAMETER_VALUE, IDENTIFYING
FROM BATCH_JOB_EXECUTION_PARAMS ORDER BY JOB_EXECUTION_ID DESC;

SELECT JOB_EXECUTION_ID, STEP_NAME, STATUS, READ_COUNT, WRITE_COUNT,
       FILTER_COUNT, PROCESS_SKIP_COUNT, COMMIT_COUNT, ROLLBACK_COUNT
FROM BATCH_STEP_EXECUTION ORDER BY STEP_EXECUTION_ID DESC;

SELECT * FROM settlement_execution_summary ORDER BY job_execution_id DESC;
```

JPA와 Spring JDBC의 SQL 문장을 출력한다. 바인딩 파라미터 TRACE는 끈 상태다.
Context는 읽을 수 있는 JSON으로 저장하고 기존 Base64 데이터도 읽는다.
핵심 개념의 연결은 [개념 정리](docs/concepts.md)를 참고한다.
