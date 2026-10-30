-- 결제
CREATE TABLE payment
(
    id                BIGSERIAL PRIMARY KEY,
    payment_key       VARCHAR(40)    NOT NULL,
    order_id          BIGINT         NOT NULL,
    method            VARCHAR(20)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    total_amount      NUMERIC(15, 2) NOT NULL,
    canceled_amount   NUMERIC(15, 2) NOT NULL DEFAULT 0,
    pg_transaction_id VARCHAR(64),
    approved_at       TIMESTAMPTZ,
    failure_reason    VARCHAR(200),
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL,
    modified_at       TIMESTAMPTZ,
    CONSTRAINT uk_payment_key UNIQUE (payment_key),
    -- 주문 하나에 결제는 하나. 애플리케이션 검사만 믿으면 동시 요청 두 건이
    -- 둘 다 통과해 이중 결제가 된다. 마지막 방어선은 DB 제약이어야 한다.
    CONSTRAINT uk_payment_order UNIQUE (order_id),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payment_total_amount CHECK (total_amount >= 0),
    CONSTRAINT ck_payment_canceled_amount CHECK (canceled_amount >= 0 AND canceled_amount <= total_amount)
);

CREATE INDEX idx_payment_status_created_at ON payment (status, created_at DESC);
CREATE INDEX idx_payment_pg_transaction_id ON payment (pg_transaction_id);

COMMENT ON TABLE payment IS '결제';
COMMENT ON COLUMN payment.pg_transaction_id IS '결제사 거래번호 (대사 기준)';
COMMENT ON COLUMN payment.version IS '낙관적 락 버전';

-- 취소 이력 (부분취소가 여러 번 가능하므로 별도 테이블)
CREATE TABLE payment_cancel
(
    id           BIGSERIAL PRIMARY KEY,
    payment_id   BIGINT         NOT NULL,
    amount       NUMERIC(15, 2) NOT NULL,
    reason       VARCHAR(200),
    pg_cancel_id VARCHAR(64),
    canceled_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_payment_cancel_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT ck_payment_cancel_amount CHECK (amount > 0)
);

CREATE INDEX idx_payment_cancel_payment_id ON payment_cancel (payment_id);

COMMENT ON TABLE payment_cancel IS '결제 취소 이력';

-- 멱등성 기록
CREATE TABLE idempotency_record
(
    id                  BIGSERIAL PRIMARY KEY,
    idempotency_key     VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64)  NOT NULL,
    resource_key        VARCHAR(40),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key)
);

COMMENT ON TABLE idempotency_record IS '멱등 요청 기록';
COMMENT ON COLUMN idempotency_record.request_fingerprint IS '요청 내용 해시. 같은 키에 다른 내용이면 거부';
COMMENT ON COLUMN idempotency_record.resource_key IS '처리 결과 리소스 키. 완료 전에는 NULL';
