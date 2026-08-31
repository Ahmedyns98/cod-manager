-- Product catalog. A product is what the seller advertises; a variant is what
-- actually ships and carries the stock.

CREATE TABLE product (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID           NOT NULL REFERENCES users (id),
    name        VARCHAR(200)   NOT NULL,
    sku         VARCHAR(64)    NOT NULL,
    base_price  NUMERIC(12, 2) NOT NULL,
    cost_price  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uk_product_sku    UNIQUE (owner_id, sku),
    CONSTRAINT ck_product_prices CHECK (base_price >= 0 AND cost_price >= 0)
);

CREATE INDEX idx_product_owner ON product (owner_id, active);

CREATE TABLE product_variant (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID         NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    size        VARCHAR(32),
    color       VARCHAR(32),
    sku         VARCHAR(64)  NOT NULL,
    stock_qty   INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_variant_sku   UNIQUE (sku),
    CONSTRAINT ck_variant_stock CHECK (stock_qty >= 0)
);

CREATE INDEX idx_variant_product ON product_variant (product_id);
