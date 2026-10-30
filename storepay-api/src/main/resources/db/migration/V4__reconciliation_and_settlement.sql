-- 대사 실행 이력
-- 영업일 하나당 결과는 하나만 유효하다. 다시 돌리면 이전 결과를 대체한다
-- (여러 번 돌린 결과가 함께 남아 있으면 "오늘 불일치가 몇 건이냐"에 답할 수 없다).
CREATE TABLE reconciliation_run
(
    id              BIGSERIAL PRIMARY KEY,
    run_key         VARCHAR(40) NOT NULL,
    business_date   DATE        NOT NULL,
    status          VARCHAR(20) NOT NULL,
    internal_count  INTEGER     NOT NULL DEFAULT 0,
    pg_count        INTEGER     NOT NULL DEFAULT 0,
    matched_count   INTEGER     NOT NULL DEFAULT 0,
    mismatch_count  INTEGER     NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    failure_reason  VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL,
    modified_at     TIMESTAMPTZ,
    CONSTRAINT uk_reconciliation_run_key UNIQUE (run_key),
    CONSTRAINT uk_reconciliation_run_business_date UNIQUE (business_date)
);

COMMENT ON TABLE reconciliation_run IS '일별 대사 실행';
COMMENT ON COLUMN reconciliation_run.business_date IS '영업일 (정산 타임존 기준)';

-- 불일치 내역
CREATE TABLE reconciliation_mismatch
(
    id                BIGSERIAL PRIMARY KEY,
    run_id            BIGINT      NOT NULL,
    type              VARCHAR(30) NOT NULL,
    payment_key       VARCHAR(40),
    pg_transaction_id VARCHAR(64),
    internal_amount   NUMERIC(15, 2),
    pg_amount         NUMERIC(15, 2),
    internal_status   VARCHAR(20),
    pg_status         VARCHAR(20),
    detail            VARCHAR(500) NOT NULL,
    CONSTRAINT fk_reconciliation_mismatch_run FOREIGN KEY (run_id) REFERENCES reconciliation_run (id)
);

CREATE INDEX idx_reconciliation_mismatch_run_id ON reconciliation_mismatch (run_id);

COMMENT ON TABLE reconciliation_mismatch IS '대사 불일치 내역 (자동 보정하지 않고 사람이 판단한다)';

-- 가맹점별 일 정산
CREATE TABLE settlement
(
    id                BIGSERIAL PRIMARY KEY,
    settlement_key    VARCHAR(40)    NOT NULL,
    merchant_id       BIGINT         NOT NULL,
    business_date     DATE           NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    transaction_count INTEGER        NOT NULL,
    approved_amount   NUMERIC(15, 2) NOT NULL,
    canceled_amount   NUMERIC(15, 2) NOT NULL,
    fee_amount        NUMERIC(15, 2) NOT NULL,
    payout_amount     NUMERIC(15, 2) NOT NULL,
    paid_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL,
    modified_at       TIMESTAMPTZ,
    CONSTRAINT uk_settlement_key UNIQUE (settlement_key),
    CONSTRAINT uk_settlement_merchant_date UNIQUE (merchant_id, business_date),
    CONSTRAINT fk_settlement_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id),
    -- fee_amount 에는 하한을 두지 않는다. 전일 매출을 당일 취소하면 수수료를 돌려주는 쪽이라
    -- 당일 수수료 합계가 음수가 될 수 있다.
    CONSTRAINT ck_settlement_amounts CHECK (approved_amount >= 0 AND canceled_amount >= 0)
);

CREATE INDEX idx_settlement_business_date ON settlement (business_date DESC);

COMMENT ON TABLE settlement IS '가맹점 일 정산';
COMMENT ON COLUMN settlement.payout_amount IS '지급액 = 승인 - 취소 - 수수료 (음수 가능: 전일 매출 당일 취소)';
