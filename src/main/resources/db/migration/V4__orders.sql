-- Customers, orders, order lines and the status audit trail.
--
-- The COD lifecycle is the reason this system exists: an order is not money.
-- It becomes money only once the carrier hands over the cash, which is why
-- DELIVERED and SETTLED are two different states.

CREATE TABLE customer (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID         NOT NULL REFERENCES users (id),
    full_name       VARCHAR(160) NOT NULL,
    phone           VARCHAR(24)  NOT NULL,
    wilaya_code     SMALLINT     NOT NULL REFERENCES wilaya (code),
    commune         VARCHAR(120) NOT NULL,
    address         VARCHAR(400),
    delivered_count INTEGER      NOT NULL DEFAULT 0,
    returned_count  INTEGER      NOT NULL DEFAULT 0,
    blacklisted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_customer_phone  UNIQUE (owner_id, phone),
    CONSTRAINT ck_customer_counts CHECK (delivered_count >= 0 AND returned_count >= 0)
);

CREATE INDEX idx_customer_phone ON customer (owner_id, phone);

CREATE TABLE orders (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id      UUID           NOT NULL REFERENCES users (id),
    order_number  VARCHAR(24)    NOT NULL,
    customer_id   UUID           NOT NULL REFERENCES customer (id),
    status        VARCHAR(32)    NOT NULL,
    source        VARCHAR(32)    NOT NULL,
    delivery_type VARCHAR(16)    NOT NULL,
    carrier       VARCHAR(32)    NOT NULL,
    wilaya_code   SMALLINT       NOT NULL REFERENCES wilaya (code),
    commune       VARCHAR(120)   NOT NULL,
    address       VARCHAR(400),
    subtotal      NUMERIC(12, 2) NOT NULL,
    delivery_fee  NUMERIC(12, 2) NOT NULL,
    total         NUMERIC(12, 2) NOT NULL,
    notes         VARCHAR(1000),
    confirmed_at  TIMESTAMPTZ,
    delivered_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uk_order_number    UNIQUE (order_number),
    CONSTRAINT ck_order_totals    CHECK (subtotal >= 0 AND delivery_fee >= 0 AND total >= 0),
    CONSTRAINT ck_order_delivery  CHECK (delivery_type IN ('HOME', 'STOPDESK')),
    CONSTRAINT ck_order_carrier   CHECK (carrier IN ('YALIDINE', 'ZREXPRESS', 'NOEST'))
);

CREATE INDEX idx_orders_owner_status ON orders (owner_id, status);
CREATE INDEX idx_orders_created      ON orders (owner_id, created_at DESC);

CREATE TABLE order_item (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    variant_id   UUID           NOT NULL REFERENCES product_variant (id),
    -- Snapshot columns. Editing a product later must never rewrite history.
    product_name VARCHAR(200)   NOT NULL,
    variant_sku  VARCHAR(64)    NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT ck_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_item_price    CHECK (unit_price >= 0)
);

CREATE INDEX idx_item_order ON order_item (order_id);

CREATE TABLE order_status_history (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status VARCHAR(32),
    to_status   VARCHAR(32)  NOT NULL,
    reason      VARCHAR(400),
    changed_by  UUID,
    changed_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_history_order ON order_status_history (order_id, changed_at);
