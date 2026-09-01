-- Parcels handed to a carrier, and the raw event stream that comes back.
--
-- carrier_event is deliberately append-only and stores the untranslated status
-- alongside our mapped one. When a carrier invents a new status, the payload is
-- already on disk and can be replayed once the mapping is updated.

CREATE TABLE shipment (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    carrier          VARCHAR(32)  NOT NULL,
    tracking_number  VARCHAR(64),
    label_url        VARCHAR(500),
    carrier_status   VARCHAR(120),
    last_synced_at   TIMESTAMPTZ,
    failure_reason   VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,

    -- One parcel per order: the guard that makes a retried create idempotent.
    CONSTRAINT uk_shipment_order    UNIQUE (order_id),
    CONSTRAINT uk_shipment_tracking UNIQUE (tracking_number),
    CONSTRAINT ck_shipment_carrier  CHECK (carrier IN ('YALIDINE', 'ZREXPRESS', 'NOEST'))
);

CREATE INDEX idx_shipment_sync ON shipment (carrier, last_synced_at);

CREATE TABLE carrier_event (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id   UUID         NOT NULL REFERENCES shipment (id) ON DELETE CASCADE,
    raw_status    VARCHAR(120) NOT NULL,
    mapped_status VARCHAR(32),
    payload       TEXT,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_event_shipment ON carrier_event (shipment_id, occurred_at DESC);
