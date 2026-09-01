-- Inbound carrier notifications.
--
-- Carriers retry webhooks until they get a 2xx, so the same notification
-- arrives two or three times as a matter of course. The unique constraint on
-- the fingerprint is what makes handling it twice harmless.

CREATE TABLE webhook_event (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    carrier       VARCHAR(32)  NOT NULL,
    fingerprint   VARCHAR(128) NOT NULL,
    tracking      VARCHAR(64),
    raw_status    VARCHAR(120),
    payload       TEXT         NOT NULL,
    processed     BOOLEAN      NOT NULL DEFAULT FALSE,
    process_error VARCHAR(500),
    received_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uk_webhook_fingerprint UNIQUE (carrier, fingerprint)
);

CREATE INDEX idx_webhook_tracking ON webhook_event (tracking, received_at DESC);
CREATE INDEX idx_webhook_unprocessed ON webhook_event (processed, received_at)
    WHERE processed = FALSE;
