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
| 운영 | Spring Actuator, Docker multi-stage build, GitHub Actions |

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

### 외부 식별자와 PK를 분리한다

API는 `ord_lz4k2x_8fh3jq91mv` 같은 키를 노출하고 bigint PK는 내보내지 않습니다.
순차 PK를 노출하면 하루 주문량이 그대로 추측되고 스크래핑 대상이 됩니다.
생성 시각(36진수) + 난수 조합이라 시간순 정렬은 유지되면서 값은 예측할 수 없습니다.

### 스키마는 Flyway만 바꾼다

`ddl-auto: validate`입니다. 엔티티와 실제 스키마가 어긋나면 **기동 단계에서 죽습니다**.
운영 중에 조용히 틀리는 것보다 낫습니다.
통합 테스트가 실제 PostgreSQL에 마이그레이션을 돌리므로, SQL 오류는 CI에서 잡힙니다.

## 도메인 모델

```
Merchant (가맹점)  1 ── N  Store (매장)  1 ── N  Product (상품)
                                 │
                                 1
                                 │
                                 N
                              Order (주문)  1 ── N  OrderItem (주문 항목)
```

상태 전이

```
Merchant : PENDING → ACTIVE → SUSPENDED → (ACTIVE 복귀 가능)
                            → TERMINATED (복귀 불가)

Order    : CREATED → PAID
              │        │
              └────────┴──→ CANCELED
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

전체 스펙은 Swagger UI를 참고하세요.

## 로드맵

- [x] 가맹점 / 매장 / 상품 / 주문 도메인, QueryDSL 조건 검색, Flyway, OpenAPI, CI
- [ ] 결제 승인 / 취소, PG사 연동 (멱등성 키, 타임아웃·재시도, 상태 머신)
- [ ] 웹훅 수신 (HMAC 서명 검증, 재전송 방지)
- [ ] 일별 가맹점 정산 배치
- [ ] POS 실시간 결제 상태 푸시 (WebSocket)
