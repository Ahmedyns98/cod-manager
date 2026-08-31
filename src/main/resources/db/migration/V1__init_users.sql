-- Baseline schema. Owner accounts for the seller-facing API.
-- Every table repeats the audit columns from BaseEntity: id, created_at,
-- updated_at, version.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    store_name    VARCHAR(120) NOT NULL,
    role          VARCHAR(32)  NOT NULL DEFAULT 'OWNER',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role  CHECK (role IN ('OWNER', 'STAFF'))
);

CREATE INDEX idx_users_active ON users (active);
