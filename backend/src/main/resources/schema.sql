-- SwiftDrop PostgreSQL Schema
-- Module: Merchant Platform, Order API & Webhooks
-- Safe to execute repeatedly: uses IF NOT EXISTS and ALTER TABLE ADD COLUMN IF NOT EXISTS.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Merchants Table
CREATE TABLE IF NOT EXISTS merchants (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL UNIQUE,
    business_name       VARCHAR(255) NOT NULL,
    registration_number VARCHAR(100),
    contact_phone       VARCHAR(50),
    business_address    TEXT,
    registration_status VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    approved_by         UUID,
    approval_notes      TEXT,
    api_key             VARCHAR(128) UNIQUE,
    webhook_url         VARCHAR(500),
    webhook_secret      VARCHAR(128),
    created_at          TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_merchants_user_id ON merchants(user_id);
CREATE INDEX IF NOT EXISTS idx_merchants_api_key ON merchants(api_key);
CREATE INDEX IF NOT EXISTS idx_merchants_status ON merchants(registration_status);

-- Parcels Table (created here if not already present from parcel-status-tracking module)
CREATE TABLE IF NOT EXISTS parcels (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tracking_number       VARCHAR(32)    NOT NULL UNIQUE,
    customer_id           UUID,
    merchant_id           UUID,
    sender_name           VARCHAR(120)   NOT NULL,
    recipient_name        VARCHAR(120)   NOT NULL,
    recipient_phone       VARCHAR(50),
    pickup_address        VARCHAR(255)   NOT NULL,
    pickup_city           VARCHAR(80)    NOT NULL,
    dropoff_address       VARCHAR(255)   NOT NULL,
    dropoff_city          VARCHAR(80)    NOT NULL,
    service_type          VARCHAR(20)    NOT NULL,
    weight_kg             NUMERIC(8, 2)  NOT NULL,
    fee                   NUMERIC(12, 2) NOT NULL,
    status                VARCHAR(30)    NOT NULL DEFAULT 'CREATED',
    estimated_delivery_at TIMESTAMPTZ,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               BIGINT         NOT NULL DEFAULT 0
);

-- In case parcels table was created by another branch before merge:
ALTER TABLE parcels ADD COLUMN IF NOT EXISTS merchant_id UUID;
ALTER TABLE parcels ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_parcels_merchant_id ON parcels(merchant_id);
CREATE INDEX IF NOT EXISTS idx_parcels_tracking_number ON parcels(tracking_number);
CREATE INDEX IF NOT EXISTS idx_parcels_status ON parcels(status);

-- Merchant Webhook Logs Table (for webhook dispatches, status codes, and retry queueing)
CREATE TABLE IF NOT EXISTS merchant_webhook_logs (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID         NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    tracking_number VARCHAR(32)  NOT NULL,
    event_type      VARCHAR(50)  NOT NULL,
    payload         TEXT         NOT NULL,
    endpoint_url    VARCHAR(500) NOT NULL,
    response_code   INT,
    delivery_status VARCHAR(30)  NOT NULL,
    error_message   TEXT,
    attempt_count   INT          NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_merchant_webhook_logs_merchant ON merchant_webhook_logs(merchant_id);
CREATE INDEX IF NOT EXISTS idx_merchant_webhook_logs_tracking ON merchant_webhook_logs(tracking_number);

