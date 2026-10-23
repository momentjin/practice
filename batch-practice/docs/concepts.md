# 코드와 함께 보는 Batch 개념

## 실행 구조

```text
JobInstance: Job 이름 + identifying 파라미터의 조합
└── JobExecution: 한 번의 실행 시도
    └── StepExecution: 단계별 실행 시도
        ├── Tasklet: 단계의 작업을 직접 수행
        └── Chunk 처리
            ├── Reader.read(): 항목 하나 반환 (DB 조회는 페이지 단위일 수 있음)
            ├── Processor.process(): 항목 변환 / null이면 Filter
            └── Writer.write(): 청크 단위 출력·저장
```

Chunk 크기는 커밋 단위, pageSize는 Reader의 조회 단위, maxItemCount는 읽기 상한이다.
DB 트랜잭션 매니저를 Chunk에 연결해야 JPA 저장·업무 집계·체크포인트가 의도대로 동작한다.
flush는 SQL 반영이며 commit은 아니다. 실패한 청크는 롤백되지만 이미 커밋한 청크는 남는다.

## 식별·재시작·멱등성

- run.id라는 이름은 특별한 예약 식별자가 아니다. 이 프로젝트의 명시적인 파라미터 정책이다.
- 기본 JobKey 생성기는 식별 파라미터를 정렬·직렬화해 해시를 만든다. 사용자에게 보이는 PK는 별도의 JOB_INSTANCE_ID다.
- 동일 JobInstance가 완료됐으면 재실행을 거부하고, 실패했다면 새 JobExecution으로 재시작한다.
- 완료한 Step은 기본적으로 건너뛴다. `allowStartIfComplete(true)`는 완료된 Step도 다시 실행하도록 바꾼다.
- Reader의 ExecutionContext가 저장한 커밋 위치를 복원한다. 입력 집합과 정렬이 실행 사이에 바뀌면 안전한 재시작을 보장하기 어렵다.
- 업무 멱등성은 별도 문제다. 다른 JobInstance도 같은 주문을 읽을 수 있으므로 기존 결과 비교와 `order_id UNIQUE`를 사용한다.
- WRITE_COUNT는 Writer에 전달해 커밋한 항목 수다. 실제 신규 INSERT 수는 공용 Writer의 SettlementWriteResult로 구분한다.

## Scope

- StepScope: StepExecution마다 객체를 만들고, 실행 파라미터/Context를 늦게 주입한다.
- 실행별 상태가 있는 Writer/Processor, 파티션별 읽기 위치가 필요한 Reader에 사용한다.
- 파라미터를 받지 않아도 실행별 상태가 있다면 필요할 수 있다.
- Step 자체에 StepScope를 붙이지 않는다. 여러 Worker에서 JobScope 객체를 공유하는 방식도 피한다.

## 오류 처리

- Retry: 일시적 오류를 같은 실행 안에서 재시도한다. 이번 Batch 6 builder의 retryLimit(2)는 최초 시도 외 재시도 최대 2회다.
- Skip: 지정한 오류의 항목을 제외하고 계속한다. skipLimit은 청크마다가 아니라 Step 실행 전체 기준이다.
- Filter: Processor가 null을 반환하는 정상적인 제외다. Skip과 별도 카운터다.
- Retry를 다 써도 Skip 대상으로 지정되지 않은 오류는 Step을 실패시킨다.
- 재시도와 롤백으로 같은 항목이 다시 호출될 수 있다. 호출 횟수를 주문 순번으로 사용하지 않는다.
- Skip 카운터만으로 어떤 주문이 왜 제외됐는지 복원할 수 없다. 운영에서 필요하면 별도 제외 이력을 저장한다.

## 병렬 처리와 제출 대기

```text
Manager Step
├── Partitioner: 작업 범위를 ExecutionContext들로 나눔
└── TaskExecutor: Worker Step 실행을 제출
    └── Worker StepExecution마다 독립된 Reader
        └── Chunk 단위 처리
```

- 파티션 수는 작업 범위의 수, 스레드 수는 동시 실행 상한이다.
- 파티션을 더 잘게 나누면 먼저 끝난 스레드가 남은 범위를 맡아 부하 쏠림을 줄일 수 있다. 파티션 관리 비용도 늘어난다.
- ThreadPoolTaskExecutor는 core 스레드 → 큐 → max까지 추가 스레드 → 거절 순서로 동작한다.
- queueCapacity=0이면 직접 전달용 SynchronousQueue를 사용한다. 제출 측을 기다리게 하는 설정은 아니다.
- SimpleAsyncTaskExecutor의 concurrencyLimit과 rejectTasksWhenLimitReached(false)는 제출 측 블로킹을 지원한다.
- CallerRunsPolicy는 제출자가 직접 작업을 수행한다. 순수한 제출 대기와 달리 제출자도 작업 실행에 참여한다.
- 실습의 Semaphore는 미완료 작업 수를 제한한다. 스레드풀 자체의 실제 수락 시점과 완전히 같은 신호는 아니므로 거절 경계를 추가 처리한다.

## 진행 집계와 운영

- Job/Step의 STARTED, COMPLETED, FAILED는 프레임워크 실행 상태다.
- Chunk 결과 집계는 저장과 같은 트랜잭션에서 변경하고, 진행 로그는 커밋 성공 후 출력한다.
- 전체 대상과 Reader의 조회 조건이 다르면 진행률이 틀린다. 재시작 이전 완료 위치와 이번 실행 집계도 나눠야 한다.
- 강제 종료는 정상 실패와 다르다. STARTED가 남을 수 있으며, 상태만 보고 아직 실행 중인 프로세스를 실패 처리하면 안 된다.
- PID·호스트·시작 시각·실행 소유권 등으로 실제 실행 종료를 확인하고 운영 복구 절차를 수행한다. 메타데이터 복구는 프로세스를 죽이거나 Job을 자동 재시작하는 동작과 다르다.
- 기본 메타데이터 테이블에 업무 컬럼을 덧붙이는 대신 별도 업무 집계 테이블을 둔다. 기본 테이블 변경이 물리적으로 불가능한 것은 아니지만, 기본 DAO/스키마가 사용자 컬럼을 관리해주지는 않는다.

## Job 간 연결과 결과 검증

- 하나의 Job 안의 순차 단계는 start/next로 연결한다.
- 서로 다른 Job은 실행 코드가 앞 Job의 최종 성공 상태를 확인하고 다음 Job을 시작한다. 실습 Runner는 기본 동기 JobOperator를 전제로 한다.
- 비동기 Job 실행으로 바꾸면 시작 요청의 반환만으로 완료를 판단하면 안 된다. 완료 대기/상태 확인을 별도로 설계한다.
- 외부 워크플로 도구로 의존성을 관리하는 것도 가능하지만 이 프로젝트에는 추가하지 않는다.
- COMPLETED만으로 업무 정확성을 보장하지 않는다. 대상 누락, 주문별 결과, 금액 합계를 원본 기준으로 검증한다.
- 마지막 검증 Step 실패는 앞서 커밋한 정산 결과를 취소하지 않는다. 후속 지급을 차단하고 원인을 수정한 뒤 재검증한다.
- 로그만 출력하는 지급 미리보기와 실제 외부 송금의 멱등성·복구는 구분한다.
