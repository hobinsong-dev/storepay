-- 수신한 결제사 웹훅
CREATE TABLE webhook_event
(
    id          BIGSERIAL PRIMARY KEY,
    event_id    VARCHAR(100) NOT NULL,
    event_type  VARCHAR(40)  NOT NULL,
    payload     TEXT         NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL,
    -- 결제사는 at-least-once 로 보낸다. 중복 수신을 막는 마지막 방어선.
    CONSTRAINT uk_webhook_event_id UNIQUE (event_id)
);

CREATE INDEX idx_webhook_event_type_received_at ON webhook_event (event_type, received_at DESC);

COMMENT ON TABLE webhook_event IS '결제사 웹훅 수신 기록 (중복 처리 방지 및 감사 추적)';
COMMENT ON COLUMN webhook_event.payload IS '수신 원문. 서명 검증 대상이자 사후 추적 근거';
