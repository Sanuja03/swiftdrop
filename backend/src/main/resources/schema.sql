-- SwiftDrop PostgreSQL schema (Supabase)
-- Applied MANUALLY via the Supabase SQL Editor (not run at startup).
-- Safe to run more than once: every statement is idempotent.

-- =====================================================================
-- Identity, Authentication & Role Management
-- Enterprise custom authentication (Spring Security + JWT)
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Users: Customer, Merchant, Courier, Dispatcher, Admin
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    provider_id VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Existing Supabase users table predates this column
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- Refresh tokens for JWT token rotation
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(512) NOT NULL UNIQUE,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);

-- Password reset tokens
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(128) NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user ON password_reset_tokens(user_id);

-- Email verification tokens
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(128) NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_token ON email_verification_tokens(token);
CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_user ON email_verification_tokens(user_id);

-- =====================================================================
-- Live Tracking, History & Receipts
-- =====================================================================

-- Minimal parcels table needed by status tracking and receipts.
-- Booking & pricing extends it further below.
CREATE TABLE IF NOT EXISTS parcels (
    id                    UUID           PRIMARY KEY,
    tracking_number       VARCHAR(32)    NOT NULL UNIQUE,
    customer_id           UUID,
    sender_name           VARCHAR(120)   NOT NULL,
    recipient_name        VARCHAR(120)   NOT NULL,
    pickup_address        VARCHAR(255)   NOT NULL,
    pickup_city           VARCHAR(80)    NOT NULL,
    dropoff_address       VARCHAR(255)   NOT NULL,
    dropoff_city          VARCHAR(80)    NOT NULL,
    service_type          VARCHAR(20)    NOT NULL,
    weight_kg             NUMERIC(8, 2)  NOT NULL,
    fee                   NUMERIC(12, 2) NOT NULL,
    status                VARCHAR(30)    NOT NULL,
    estimated_delivery_at TIMESTAMPTZ,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version               BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_parcels_customer_id ON parcels (customer_id);

-- One row per status change; drives the customer's tracking timeline.
CREATE TABLE IF NOT EXISTS parcel_status_history (
    id          UUID         PRIMARY KEY,
    parcel_id   UUID         NOT NULL REFERENCES parcels (id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status   VARCHAR(30)  NOT NULL,
    note        VARCHAR(500),
    changed_by  UUID,
    changed_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_parcel_status_history_parcel
    ON parcel_status_history (parcel_id, changed_at);

-- One receipt per parcel, issued the first time it is requested.
CREATE TABLE IF NOT EXISTS receipts (
    id             UUID           PRIMARY KEY,
    parcel_id      UUID           NOT NULL UNIQUE REFERENCES parcels (id) ON DELETE CASCADE,
    receipt_number VARCHAR(40)    NOT NULL UNIQUE,
    amount         NUMERIC(12, 2) NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    issued_at      TIMESTAMPTZ    NOT NULL
);

-- =====================================================================
-- Booking & Pricing: zones and service pricing
-- =====================================================================
CREATE TABLE IF NOT EXISTS zones (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    surcharge NUMERIC(12, 2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS pricing_rules (
    id UUID PRIMARY KEY,
    service_type VARCHAR(20) NOT NULL UNIQUE,
    base_fee NUMERIC(12, 2) NOT NULL,
    per_kg_fee NUMERIC(12, 2) NOT NULL,
    inter_zone_fee NUMERIC(12, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO zones (id, code, name, surcharge, active) VALUES
    ('10000000-0000-4000-8000-000000000001', 'COLOMBO', 'Colombo', 0, TRUE),
    ('10000000-0000-4000-8000-000000000002', 'GAMPAHA', 'Gampaha', 0, TRUE),
    ('10000000-0000-4000-8000-000000000003', 'KALUTARA', 'Kalutara', 0, TRUE),
    ('10000000-0000-4000-8000-000000000004', 'KANDY', 'Kandy', 150, TRUE),
    ('10000000-0000-4000-8000-000000000005', 'GALLE', 'Galle', 150, TRUE),
    ('10000000-0000-4000-8000-000000000006', 'MATARA', 'Matara', 150, TRUE),
    ('10000000-0000-4000-8000-000000000007', 'KURUNEGALA', 'Kurunegala', 150, TRUE),
    ('10000000-0000-4000-8000-000000000008', 'JAFFNA', 'Jaffna', 350, TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    surcharge = EXCLUDED.surcharge,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO pricing_rules (id, service_type, base_fee, per_kg_fee, inter_zone_fee, active) VALUES
    ('20000000-0000-4000-8000-000000000001', 'STANDARD', 590, 100, 250, TRUE),
    ('20000000-0000-4000-8000-000000000002', 'EXPRESS', 990, 100, 400, TRUE),
    ('20000000-0000-4000-8000-000000000003', 'SAME_DAY', 1490, 100, 600, TRUE)
ON CONFLICT (service_type) DO UPDATE SET
    base_fee = EXCLUDED.base_fee,
    per_kg_fee = EXCLUDED.per_kg_fee,
    inter_zone_fee = EXCLUDED.inter_zone_fee,
    active = EXCLUDED.active,
    updated_at = now();

-- =====================================================================
-- Booking & Pricing: parcel booking columns            (added 2026-11-13)
-- Additive and idempotent: every new column is nullable, so existing
-- parcels and the tracking/receipt code are untouched.
-- parcels.fee stays the TOTAL fee; the three *_fee/*_charge columns are its
-- breakdown. parcels.tracking_number is already NOT NULL UNIQUE (see above).
-- =====================================================================
ALTER TABLE parcels ADD COLUMN IF NOT EXISTS pickup_zone_id  UUID REFERENCES zones (id);
ALTER TABLE parcels ADD COLUMN IF NOT EXISTS dropoff_zone_id UUID REFERENCES zones (id);
ALTER TABLE parcels ADD COLUMN IF NOT EXISTS parcel_size     VARCHAR(20);
ALTER TABLE parcels ADD COLUMN IF NOT EXISTS base_fee        NUMERIC(12, 2);
ALTER TABLE parcels ADD COLUMN IF NOT EXISTS weight_charge   NUMERIC(12, 2);
ALTER TABLE parcels ADD COLUMN IF NOT EXISTS zone_charge     NUMERIC(12, 2);
ALTER TABLE parcels ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(30);

CREATE INDEX IF NOT EXISTS idx_parcels_pickup_zone_id  ON parcels (pickup_zone_id);
CREATE INDEX IF NOT EXISTS idx_parcels_dropoff_zone_id ON parcels (dropoff_zone_id);
-- ===================== end Booking & Pricing section =====================

-- =====================================================================
-- Messaging & Webhook Engine: transactional outbox and webhook deliveries
-- Additive and idempotent. merchant_id / parcel_id are plain UUIDs (no FK)
-- so this section can be applied before the merchant tables exist.
-- =====================================================================

-- Events written in the same transaction as the business change, then
-- relayed to RabbitMQ (exchange "swiftdrop.events") by OutboxPublisher.
CREATE TABLE IF NOT EXISTS outbox_events (
    id                UUID          PRIMARY KEY,
    aggregate_type    VARCHAR(50)   NOT NULL,
    aggregate_id      UUID          NOT NULL,
    event_type        VARCHAR(100)  NOT NULL,
    routing_key       VARCHAR(150)  NOT NULL,
    payload           TEXT          NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    publish_attempts  INT           NOT NULL DEFAULT 0,
    last_error        VARCHAR(500),
    created_at        TIMESTAMPTZ   NOT NULL,
    available_at      TIMESTAMPTZ   NOT NULL,
    published_at      TIMESTAMPTZ
);

-- The relay only ever looks for pending rows that are due.
CREATE INDEX IF NOT EXISTS idx_outbox_events_pending
    ON outbox_events (available_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id);

-- One row per (event, merchant): the signed body, its current state and the
-- outcome of the latest attempt. Read by the delivery-attempts API.
CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id                  UUID          PRIMARY KEY,
    event_id            UUID          NOT NULL,
    merchant_id         UUID          NOT NULL,
    parcel_id           UUID,
    tracking_number     VARCHAR(32),
    event_type          VARCHAR(100)  NOT NULL,
    endpoint_url        VARCHAR(500)  NOT NULL,
    payload             TEXT          NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    attempt_count       INT           NOT NULL DEFAULT 0,
    max_attempts        INT           NOT NULL,
    last_response_code  INT,
    last_error          VARCHAR(500),
    last_attempt_at     TIMESTAMPTZ,
    next_attempt_at     TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_webhook_deliveries_event_merchant UNIQUE (event_id, merchant_id)
);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_merchant
    ON webhook_deliveries (merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_status
    ON webhook_deliveries (status);
-- ================= end Messaging & Webhook Engine section =================
