-- Carrier payouts.
--
-- A delivered order is not paid money: the carrier holds the cash and settles
-- it days later in one lump transfer. This is where an order finally becomes
-- revenue, and where the gap between the two is visible.

CREATE TABLE remittance (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id       UUID           NOT NULL REFERENCES users (id),
    carrier        VARCHAR(32)    NOT NULL,
    reference      VARCHAR(64)    NOT NULL,
    declared_total NUMERIC(14, 2) NOT NULL,
    matched_total  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    line_count     INTEGER        NOT NULL DEFAULT 0,
    matched_count  INTEGER        NOT NULL DEFAULT 0,
    received_at    DATE           NOT NULL,
    source_file    VARCHAR(255),
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,

    -- Re-uploading the same payout file must not double-count it.
    CONSTRAINT uk_remittance_ref     UNIQUE (owner_id, carrier, reference),
    CONSTRAINT ck_remittance_carrier CHECK (carrier IN ('YALIDINE', 'ZREXPRESS', 'NOEST'))
);

CREATE INDEX idx_remittance_owner ON remittance (owner_id, received_at DESC);

CREATE TABLE remittance_line (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    remittance_id     UUID           NOT NULL REFERENCES remittance (id) ON DELETE CASCADE,
    order_id          UUID           REFERENCES orders (id),
    tracking          VARCHAR(64)    NOT NULL,
    collected_amount  NUMERIC(12, 2) NOT NULL,
    carrier_fee       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    net_amount        NUMERIC(12, 2) NOT NULL,
    expected_amount   NUMERIC(12, 2),
    status            VARCHAR(24)    NOT NULL,
    note              VARCHAR(400),
    source_row        INTEGER,
    created_at        TIMESTAMPTZ    NOT NULL,

    CONSTRAINT ck_line_status CHECK (
        status IN ('SETTLED', 'AMOUNT_MISMATCH', 'UNKNOWN_TRACKING', 'NOT_DELIVERED', 'ALREADY_SETTLED'))
);

CREATE INDEX idx_line_remittance ON remittance_line (remittance_id, status);
CREATE INDEX idx_line_order      ON remittance_line (order_id);
