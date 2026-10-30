# storepay

클라우드 POS 결제 백엔드 API.

가맹점이 여러 매장을 운영하고, 각 매장의 POS 단말이 주문을 만들고 결제하는 구조를 다룹니다.
"동작하는 CRUD"보다 **결제 도메인에서 실제로 문제가 되는 지점들**(금액 신뢰, 동시성, 외부 PG 연동의 불확실성)을
어떻게 설계로 막았는지를 보여주는 데 목적이 있습니다.

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 언어 / 런타임 | Java 17 |
| 프레임워크 | Spring Boot 3.5, Spring Data JPA |
| 쿼리 | QueryDSL 5.1 (동적 조건 검색) |
| 데이터베이스 | PostgreSQL 16, Flyway (스키마 버전 관리) |
| 문서화 | springdoc-openapi (Swagger UI) |
| 테스트 | JUnit 5, AssertJ, Testcontainers |
| 운영 | Spring Actuator, Docker multi-stage build (jlink), GitHub Actions |

## 실행

```bash
# PostgreSQL + API 한 번에
docker compose up --build

# 또는 DB만 띄우고 애플리케이션은 로컬 실행
docker compose up -d postgres
./gradlew :storepay-api:bootRun
```

- API 문서: http://localhost:8080/swagger-ui.html
- 헬스체크: http://localhost:8080/actuator/health

## 테스트

```bash
./gradlew test
```

도메인 단위 테스트는 Docker 없이 바로 돌아갑니다.
통합 테스트는 Testcontainers로 실제 PostgreSQL 16을 띄워 **Flyway 마이그레이션과 JPA 매핑이
서로 맞는지까지** 검증하며, Docker가 없는 환경에서는 실패 대신 건너뜁니다.

> colima를 쓴다면 소켓 경로를 알려줘야 Testcontainers가 Docker를 인식합니다.
> ```bash
> export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
> export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
> ```

## 설계 메모

구현하면서 내린 판단들을 적어 둡니다. 코드만으로는 "왜 이렇게 했는지"가 안 보이는 것들입니다.

### 금액은 클라이언트를 믿지 않는다

주문 생성 API는 총액 필드를 **받지 않습니다**. 매장 상품 마스터의 단가를 서버가 다시 읽어서 계산합니다.
POS 단말은 물리적으로 가맹점 손에 있고, 요청을 조작해 1원 결제를 만드는 건 어렵지 않습니다.
`Order.place()`가 유일한 생성 경로이고 `totalAmount`에는 setter가 없습니다.

### 단가는 주문 시점에 스냅샷으로 고정한다

`order_item`에 `product_name`과 `unit_price`를 복사해 둡니다.
상품 마스터를 조인해 금액을 계산하면, 나중에 가격을 올렸을 때 **과거 주문의 금액과 영수증이 같이 바뀝니다**.
정산과 세금계산서가 걸린 영역이라 과거 데이터는 불변이어야 합니다.

### 주문에 낙관적 락을 건다

`@Version`을 둡니다. 한 매장에 POS 단말이 여러 대인 환경에서는
같은 주문에 대한 결제 승인과 취소가 실제로 동시에 들어옵니다.
비관적 락은 POS 트래픽 패턴에서 대기가 길어지므로, 충돌 빈도가 낮은 이 경우엔 낙관적 락이 맞습니다.

### 페이징과 fetch join을 분리한다

컬렉션을 fetch join 하면서 동시에 페이징하면 Hibernate가 전체를 메모리로 읽어 들입니다
(`HHH000104` 경고). `OrderQueryRepositoryImpl.search()`는
① 조건에 맞는 주문 ID 페이지를 먼저 뽑고 ② 그 ID로 항목을 fetch join 하는 2단계로 나눕니다.
쿼리는 두 번이지만 N+1도 없고 페이징도 정확합니다.
`PageableExecutionUtils`로 count 쿼리가 불필요한 경우엔 아예 날리지 않습니다.

### 결제 승인은 멱등키로 선점한 뒤에만 결제사를 부른다

POS 단말은 응답을 못 받으면 같은 요청을 다시 보내고, 손님이 결제 버튼을 두 번 누르기도 합니다.
막지 않으면 카드가 두 번 긁힙니다. 그래서 `Idempotency-Key` 헤더를 필수로 받고,
키를 선점한 뒤에만 승인을 겁니다. 같은 키로 다시 오면 처음 결과를 그대로 돌려주고,
같은 키에 다른 내용이 오면 409 로 거부합니다.

기록은 Redis 가 아니라 **DB** 에 둡니다. 중복 방지는 결제 레코드 생성과 같은 트랜잭션 경계에서
다뤄져야 하는데, 캐시에 두면 캐시만 날아갔을 때 중복 승인이 그대로 뚫립니다.
응답 속도보다 중복 결제를 막는 쪽이 중요합니다.

선점 메서드는 전부 `REQUIRES_NEW` 입니다. 바깥 트랜잭션에 묶이면
유니크 제약 위반이 전체를 rollback-only 로 만들어 "이미 처리된 요청입니다" 응답조차 못 주고,
선점이 커밋되지 않아 동시 요청이 그대로 통과합니다.
또 **조회를 먼저, 삽입을 마지막**에 둡니다 — 제약 위반이 난 세션은 그 뒤 어떤 조회도 안전하지 않습니다
(Hibernate 가 flush 시점에 `AssertionFailure` 를 던집니다).

마지막 방어선은 DB 제약입니다. `payment` 테이블의 `order_id` 에 UNIQUE 를 걸어,
애플리케이션 검사를 동시 요청 두 건이 모두 통과하더라도 이중 결제가 저장되지 않게 했습니다.

### 결제사 재시도는 타임아웃에만 건다

4xx 는 결제사가 요청을 보고 거절한 것이라 다시 보내도 같은 답이 옵니다.
5xx 는 더 위험합니다 — 승인은 이미 처리됐는데 응답만 못 받았을 수 있어, 재시도하면 이중 결제가 됩니다.
그래서 재시도는 **연결 실패와 타임아웃에만** 겁니다.
읽기 타임아웃도 짧게 잡지 않습니다. 끊어도 결제사 쪽에는 승인이 남아 미결 거래가 생기고,
그 복구 비용이 기다리는 비용보다 큽니다.

### 승인 실패는 예외로 날리지 않는다

PG 가 거절하면 결제 건을 `FAILED` 로 **남깁니다**. 예외를 던져 롤백하면 실패 사유가 사라져
고객 문의에 답할 수 없습니다. 주문은 `CREATED` 로 남아 다시 결제할 수 있습니다.

### 응답 DTO 는 트랜잭션 안에서 만든다

`open-in-view: false` 이므로 컨트롤러가 돌 때는 영속성 컨텍스트가 이미 닫혀 있습니다.
서비스가 엔티티를 반환하고 컨트롤러에서 DTO 로 매핑하면,
지연 로딩 필드에 닿는 순간 `LazyInitializationException` 이 납니다.

조회 API 라면 500 하나로 끝나지만 **쓰기 API 에서는 훨씬 나쁩니다.**
주문 취소를 예로 들면 트랜잭션은 이미 커밋돼 DB 상태는 `CANCELED` 인데
응답만 500 으로 나갑니다. 클라이언트는 실패로 알고 재시도하고, 서버는 이미 취소된 상태입니다.
결제 시스템에서 가장 피해야 할 종류의 불일치입니다.

그래서 응답 DTO 조립을 애플리케이션 레이어(`@Transactional` 안)로 내렸습니다.
엔티티는 레이어 밖으로 나가지 않습니다.
`OrderApiIntegrationTest` 는 `@Transactional` 없이 돌아갑니다 —
테스트에 트랜잭션을 걸면 세션이 열린 채라 이 버그를 못 잡기 때문입니다.

### 조건 검색은 Spring Data 프래그먼트가 아니라 포트로 붙였다

QueryDSL 조건 검색을 `OrderRepositoryImpl` 같은 Spring Data 커스텀 프래그먼트로 붙이면,
구현체가 **인터페이스와 같은 패키지에 있어야만** 탐색됩니다
(다른 패키지에 두면 `No property 'search' found for type 'Order'`로 기동이 실패합니다).
그 제약을 따르면 QueryDSL 코드가 도메인 패키지로 끌려 들어옵니다.

그래서 `OrderQueryRepository`를 도메인이 선언하는 포트로 두고,
QueryDSL 구현은 `infrastructure`에 어댑터로 뒀습니다. 의존 방향이 한쪽으로만 흐릅니다.

### 외부 식별자와 PK를 분리한다

API는 `ord_lz4k2x_8fh3jq91mv` 같은 키를 노출하고 bigint PK는 내보내지 않습니다.
순차 PK를 노출하면 하루 주문량이 그대로 추측되고 스크래핑 대상이 됩니다.
생성 시각(36진수) + 난수 조합이라 시간순 정렬은 유지되면서 값은 예측할 수 없습니다.

### 스키마는 Flyway만 바꾼다

`ddl-auto: validate`입니다. 엔티티와 실제 스키마가 어긋나면 **기동 단계에서 죽습니다**.
운영 중에 조용히 틀리는 것보다 낫습니다.
통합 테스트가 실제 PostgreSQL에 마이그레이션을 돌리므로, SQL 오류는 CI에서 잡힙니다.

### 실행 이미지는 jlink 로 줄였다

풀 JDK 를 그대로 실행 단계에 쓰면 576MB 입니다. 대부분이 런타임에 쓰지 않는 컴파일러와 도구입니다.
`jlink` 로 실제 필요한 모듈만 골라 런타임을 만들고 `alpine` 위에 얹어 **230MB** 로 줄였습니다.

베이스 이미지는 `amazoncorretto:17-alpine` 입니다.
`eclipse-temurin` 의 alpine 태그는 amd64 만 제공해서 Apple Silicon 에서 빌드가 깨집니다.

## 도메인 모델

```
Merchant (가맹점)  1 ── N  Store (매장)  1 ── N  Product (상품)
                                 │
                                 1
                                 │
                                 N
                              Order (주문)  1 ── N  OrderItem (주문 항목)
                                 │
                                 1
                                 │
                                 1
                             Payment (결제)  1 ── N  PaymentCancel (취소 이력)
```

상태 전이

```
Merchant : PENDING → ACTIVE → SUSPENDED → (ACTIVE 복귀 가능)
                            → TERMINATED (복귀 불가)

Order    : CREATED → PAID
              │        │
              └────────┴──→ CANCELED

Payment  : READY → APPROVED → PARTIAL_CANCELED → CANCELED
              │        │                             ↑
              │        └─────────────────────────────┘
              └──→ FAILED
```

## API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/merchants` | 가맹점 등록 |
| POST | `/api/v1/merchants/{merchantKey}/activate` | 가맹점 활성화 |
| POST | `/api/v1/stores` | 매장 개설 |
| POST | `/api/v1/products` | 상품 등록 |
| PATCH | `/api/v1/products/{productKey}/status` | 판매 상태 변경 |
| POST | `/api/v1/orders` | 주문 생성 |
| GET | `/api/v1/orders` | 주문 검색 (매장 / 상태 / 기간 / 금액 범위) |
| POST | `/api/v1/orders/{orderKey}/cancel` | 주문 취소 |
| POST | `/api/v1/payments` | 결제 승인 (`Idempotency-Key` 헤더 필수) |
| POST | `/api/v1/payments/{paymentKey}/cancel` | 결제 취소 (부분 취소 지원) |
| GET | `/api/v1/payments/{paymentKey}` | 결제 단건 조회 |

전체 스펙은 Swagger UI를 참고하세요.

## 로드맵

- [x] 가맹점 / 매장 / 상품 / 주문 도메인, QueryDSL 조건 검색, Flyway, OpenAPI, CI
- [x] 결제 승인 / 취소 / 부분취소, 멱등성 키, PG 연동 포트·어댑터, 타임아웃·재시도 정책
- [ ] 웹훅 수신 (HMAC 서명 검증, 재전송 방지)
- [ ] 모의 PG사 모듈 (실제 HTTP 왕복)
- [ ] 일별 가맹점 정산 배치
- [ ] POS 실시간 결제 상태 푸시 (WebSocket)
