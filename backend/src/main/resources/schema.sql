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
