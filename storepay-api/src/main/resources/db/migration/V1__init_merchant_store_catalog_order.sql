-- 가맹점
CREATE TABLE merchant
(
    id              BIGSERIAL PRIMARY KEY,
    merchant_key    VARCHAR(40)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    business_number VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    modified_at     TIMESTAMPTZ,
    CONSTRAINT uk_merchant_key UNIQUE (merchant_key),
    CONSTRAINT uk_merchant_business_number UNIQUE (business_number)
);

COMMENT ON TABLE merchant IS '가맹점';
COMMENT ON COLUMN merchant.merchant_key IS '외부 노출 식별자';

-- 매장
CREATE TABLE store
(
    id          BIGSERIAL PRIMARY KEY,
    store_key   VARCHAR(40)  NOT NULL,
    merchant_id BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    address     VARCHAR(255),
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    modified_at TIMESTAMPTZ,
    CONSTRAINT uk_store_key UNIQUE (store_key),
    CONSTRAINT fk_store_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id)
);

CREATE INDEX idx_store_merchant_id ON store (merchant_id);

COMMENT ON TABLE store IS '매장';

-- 상품
CREATE TABLE product
(
    id          BIGSERIAL PRIMARY KEY,
    product_key VARCHAR(40)    NOT NULL,
    store_id    BIGINT         NOT NULL,
    name        VARCHAR(100)   NOT NULL,
    unit_price  NUMERIC(15, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL,
    modified_at TIMESTAMPTZ,
    CONSTRAINT uk_product_key UNIQUE (product_key),
    CONSTRAINT fk_product_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT ck_product_unit_price CHECK (unit_price >= 0)
);

CREATE INDEX idx_product_store_id_status ON product (store_id, status);

COMMENT ON TABLE product IS '매장 상품';

-- 주문 (order 는 예약어라 테이블명은 orders)
CREATE TABLE orders
(
    id           BIGSERIAL PRIMARY KEY,
    order_key    VARCHAR(40)    NOT NULL,
    store_id     BIGINT         NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    total_amount NUMERIC(15, 2) NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ    NOT NULL,
    modified_at  TIMESTAMPTZ,
    CONSTRAINT uk_orders_key UNIQUE (order_key),
    CONSTRAINT fk_orders_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT ck_orders_total_amount CHECK (total_amount >= 0)
);

-- 매장별 기간 조회가 가장 잦은 패턴이라 (store_id, created_at) 복합 인덱스를 둔다.
CREATE INDEX idx_orders_store_id_created_at ON orders (store_id, created_at DESC);
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at DESC);

COMMENT ON TABLE orders IS '주문';
COMMENT ON COLUMN orders.version IS '낙관적 락 버전';

-- 주문 항목
CREATE TABLE order_item
(
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    product_name VARCHAR(100)   NOT NULL,
    unit_price   NUMERIC(15, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    line_amount  NUMERIC(15, 2) NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_order_item_order_id ON order_item (order_id);

COMMENT ON TABLE order_item IS '주문 항목 (주문 시점 상품명/단가 스냅샷 보관)';
