-- Version 001: reviewed DDL snapshot of schema.sql at 5f222a2.
-- Immutable after deployment. Add a new numbered migration for later changes.
-- No users, approval states, business names, prices, secrets or parcel links are backfilled.
-- No seed DML, RLS policy changes, row deletion or ownership guessing is included.
SELECT set_config('swiftdrop.preexisting_tables', coalesce((
 SELECT jsonb_agg(c.relname) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind IN ('r','p')
), '[]'::jsonb)::text, false);

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

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

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(512) NOT NULL UNIQUE,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(128) NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(128) NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS parcel_status_history (
    id          UUID         PRIMARY KEY,
    parcel_id   UUID         NOT NULL REFERENCES parcels (id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status   VARCHAR(30)  NOT NULL,
    note        VARCHAR(500),
    changed_by  UUID,
    changed_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE IF NOT EXISTS receipts (
    id             UUID           PRIMARY KEY,
    parcel_id      UUID           NOT NULL UNIQUE REFERENCES parcels (id) ON DELETE CASCADE,
    receipt_number VARCHAR(40)    NOT NULL UNIQUE,
    amount         NUMERIC(12, 2) NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    issued_at      TIMESTAMPTZ    NOT NULL
);

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

CREATE TABLE IF NOT EXISTS courier_assignments (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id     UUID         NOT NULL REFERENCES parcels(id) ON DELETE CASCADE,
    courier_id    UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    dispatcher_id UUID         NOT NULL REFERENCES users(id),
    status        VARCHAR(30)  NOT NULL DEFAULT 'ASSIGNED',
    notes         VARCHAR(500),
    scheduled_date DATE        NOT NULL DEFAULT CURRENT_DATE,
    assigned_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id     VARCHAR(64)  NOT NULL,
    user_id        UUID         REFERENCES users(id) ON DELETE SET NULL,
    sender         VARCHAR(20)  NOT NULL,
    content        TEXT         NOT NULL,
    tool_name      VARCHAR(50),
    tool_arguments TEXT,
    tool_result    TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

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

CREATE TABLE IF NOT EXISTS merchants (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL UNIQUE REFERENCES users(id),
    business_name       VARCHAR(255) NOT NULL,
    registration_number VARCHAR(100),
    contact_phone       VARCHAR(50),
    business_address    TEXT,
    registration_status VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    approved_by         UUID,
    approval_notes      TEXT,
    api_key             VARCHAR(128) UNIQUE,
    webhook_url         VARCHAR(500),
    webhook_secret      VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS couriers (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS delivery_proofs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id         UUID NOT NULL REFERENCES parcels(id),
    courier_id        UUID NOT NULL REFERENCES users(id),
    recipient_name    VARCHAR(150),
    photo_object_key  VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    previous_proof_id UUID REFERENCES delivery_proofs(id),
    change_reason     VARCHAR(500),
    CONSTRAINT chk_proof_content CHECK (
        NULLIF(BTRIM(recipient_name), '') IS NOT NULL
        OR NULLIF(BTRIM(photo_object_key), '') IS NOT NULL
    ),
    CONSTRAINT chk_proof_change_reason CHECK (
        previous_proof_id IS NULL OR NULLIF(BTRIM(change_reason), '') IS NOT NULL
    )
);

CREATE TABLE IF NOT EXISTS notifications (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id          UUID UNIQUE,
    parcel_id         UUID REFERENCES parcels(id) ON DELETE SET NULL,
    title             VARCHAR(150) NOT NULL,
    message           TEXT NOT NULL,
    notification_type VARCHAR(50) NOT NULL DEFAULT 'PARCEL_STATUS',
    is_read           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at           TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS report_snapshots (
    id         UUID PRIMARY KEY,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    start_date DATE NOT NULL,
    end_date   DATE NOT NULL,
    data_json  TEXT NOT NULL,
    CONSTRAINT report_snapshots_date_order CHECK (start_date <= end_date)
);

ALTER TABLE parcels ADD COLUMN IF NOT EXISTS pickup_zone_id  UUID REFERENCES zones (id);

ALTER TABLE parcels ADD COLUMN IF NOT EXISTS dropoff_zone_id UUID REFERENCES zones (id);

ALTER TABLE parcels ADD COLUMN IF NOT EXISTS parcel_size     VARCHAR(20);

ALTER TABLE parcels ADD COLUMN IF NOT EXISTS base_fee        NUMERIC(12, 2);

ALTER TABLE parcels ADD COLUMN IF NOT EXISTS weight_charge   NUMERIC(12, 2);

ALTER TABLE parcels ADD COLUMN IF NOT EXISTS zone_charge     NUMERIC(12, 2);

ALTER TABLE parcels ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(30);

ALTER TABLE courier_assignments ALTER COLUMN assigned_at SET DEFAULT now();

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS business_name VARCHAR(255);

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS registration_number VARCHAR(100);

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(50);

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS business_address TEXT;

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS registration_status VARCHAR(50);

ALTER TABLE merchants ALTER COLUMN registration_status SET DEFAULT 'PENDING';

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS approved_by UUID;

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS approval_notes TEXT;

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS api_key VARCHAR(128) UNIQUE;

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS webhook_url VARCHAR(500);

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS webhook_secret VARCHAR(255);

ALTER TABLE parcels ADD COLUMN IF NOT EXISTS merchant_id UUID REFERENCES merchants(id);

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS event_id UUID;

-- Previously missing optional fields are added before their indexes.
ALTER TABLE delivery_proofs ADD COLUMN IF NOT EXISTS previous_proof_id UUID REFERENCES delivery_proofs(id);
ALTER TABLE delivery_proofs ADD COLUMN IF NOT EXISTS change_reason VARCHAR(500);
-- Do not invent historical scheduling dates: NULL old rows stop migration 003 for review.
ALTER TABLE courier_assignments ADD COLUMN IF NOT EXISTS scheduled_date DATE;
ALTER TABLE courier_assignments ALTER COLUMN scheduled_date SET DEFAULT CURRENT_DATE;
ALTER TABLE courier_assignments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
-- Merchant order creation uses NULL customer_id; existing customer links are preserved.
ALTER TABLE parcels ALTER COLUMN customer_id DROP NOT NULL;
-- Widen only. Never narrow a larger legacy secret column or rewrite any secret.
DO $widen$
DECLARE width integer; kind text;
BEGIN
 SELECT character_maximum_length, data_type INTO width, kind FROM information_schema.columns
 WHERE table_schema='public' AND table_name='merchants' AND column_name='webhook_secret';
 IF kind='character varying' AND width < 255 THEN
  ALTER TABLE merchants ALTER COLUMN webhook_secret TYPE VARCHAR(255);
 END IF;
END;
$widen$;

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token ON password_reset_tokens(token);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user ON password_reset_tokens(user_id);

CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_token ON email_verification_tokens(token);

CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_user ON email_verification_tokens(user_id);

CREATE INDEX IF NOT EXISTS idx_parcels_customer_id ON parcels (customer_id);

CREATE INDEX IF NOT EXISTS idx_parcel_status_history_parcel
    ON parcel_status_history (parcel_id, changed_at);

CREATE INDEX IF NOT EXISTS idx_parcels_pickup_zone_id  ON parcels (pickup_zone_id);

CREATE INDEX IF NOT EXISTS idx_parcels_dropoff_zone_id ON parcels (dropoff_zone_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_active_courier_assignment
    ON courier_assignments (parcel_id)
    WHERE status IN ('ASSIGNED', 'ACCEPTED');

CREATE INDEX IF NOT EXISTS idx_courier_assignments_courier_status
    ON courier_assignments (courier_id, status);

CREATE INDEX IF NOT EXISTS idx_courier_assignments_parcel
    ON courier_assignments (parcel_id);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session
    ON chat_messages (session_id, created_at);

CREATE INDEX IF NOT EXISTS idx_chat_messages_user
    ON chat_messages (user_id);

CREATE INDEX IF NOT EXISTS idx_outbox_events_pending
    ON outbox_events (available_at) WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_merchant
    ON webhook_deliveries (merchant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_status
    ON webhook_deliveries (status);

CREATE INDEX IF NOT EXISTS idx_merchants_user_id ON merchants(user_id);

CREATE INDEX IF NOT EXISTS idx_merchants_api_key ON merchants(api_key);

CREATE INDEX IF NOT EXISTS idx_merchants_status ON merchants(registration_status);

CREATE INDEX IF NOT EXISTS idx_parcels_merchant_id ON parcels(merchant_id);

CREATE INDEX IF NOT EXISTS idx_merchant_webhook_logs_merchant ON merchant_webhook_logs(merchant_id);

CREATE INDEX IF NOT EXISTS idx_merchant_webhook_logs_tracking ON merchant_webhook_logs(tracking_number);

CREATE INDEX IF NOT EXISTS idx_delivery_proofs_parcel_created
    ON delivery_proofs (parcel_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_delivery_proofs_initial
    ON delivery_proofs (parcel_id) WHERE previous_proof_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_delivery_proofs_successor
    ON delivery_proofs (previous_proof_id) WHERE previous_proof_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON notifications (user_id, is_read);

CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_event_id
    ON notifications (event_id) WHERE event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_report_snapshots_created
    ON report_snapshots (created_at DESC, id DESC);

-- Preserve RLS/policies on existing tables. Match the baseline for newly introduced tables.
DO $new_rls$
BEGIN
 IF NOT (current_setting('swiftdrop.preexisting_tables')::jsonb ? 'couriers') THEN ALTER TABLE couriers ENABLE ROW LEVEL SECURITY; END IF;
 IF NOT (current_setting('swiftdrop.preexisting_tables')::jsonb ? 'delivery_proofs') THEN ALTER TABLE delivery_proofs ENABLE ROW LEVEL SECURITY; END IF;
 IF NOT (current_setting('swiftdrop.preexisting_tables')::jsonb ? 'report_snapshots') THEN ALTER TABLE report_snapshots ENABLE ROW LEVEL SECURITY; END IF;
END;
$new_rls$;
