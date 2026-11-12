-- SwiftDrop PostgreSQL schema (Supabase)
-- Module: Live Tracking, History & Receipts
-- Safe to run more than once: every statement uses IF NOT EXISTS.

-- Minimal parcels table needed by status tracking and receipts.
-- Booking & pricing will extend it with further columns.
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

-- Zones and service pricing used by booking estimates.
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
-- Run manually on Supabase. Additive and idempotent: every new column is
-- nullable, so existing parcels and the tracking/receipt code are untouched.
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
