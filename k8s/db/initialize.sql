\set ON_ERROR_STOP on
-- One session owns the lock through baseline, additive scripts and completion marker.
SELECT pg_advisory_lock(734510921);
SET search_path = public;

SELECT NOT EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
) AS bootstrap_new_database \gset

\if :bootstrap_new_database
    \echo 'Empty public schema: applying repository baseline once.'
    BEGIN;
    \ir /db/001-schema.sql
    COMMIT;
\else
    \echo 'Existing public schema: preserving data and skipping baseline/upsert seeds.'
\endif

-- Refuse to guess how to upgrade an older/incomplete baseline. No upsert seeds are replayed.
DO $check$
DECLARE missing_tables TEXT;
        missing_columns TEXT;
BEGIN
    SELECT string_agg(table_name, ', ') INTO missing_tables
    FROM unnest(ARRAY['users', 'refresh_tokens', 'password_reset_tokens',
         'email_verification_tokens', 'parcels', 'parcel_status_history',
         'receipts', 'zones', 'pricing_rules', 'outbox_events', 'webhook_deliveries']) AS t(table_name)
    WHERE to_regclass(format('public.%I', table_name)) IS NULL;
    IF missing_tables IS NOT NULL THEN
        RAISE EXCEPTION 'Existing database lacks baseline tables (%). Review additive SQL manually; baseline seeds will not be replayed.', missing_tables;
    END IF;
    SELECT string_agg(required.table_name || '.' || required.column_name, ', ') INTO missing_columns
    FROM (VALUES ('users', 'email_verified'), ('parcels', 'pickup_zone_id'),
                 ('parcels', 'dropoff_zone_id'), ('parcels', 'parcel_size'),
                 ('parcels', 'base_fee'), ('parcels', 'weight_charge'),
                 ('parcels', 'zone_charge'), ('parcels', 'recipient_phone')) AS required(table_name, column_name)
    WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns c
                      WHERE c.table_schema = 'public' AND c.table_name = required.table_name
                        AND c.column_name = required.column_name);
    IF missing_columns IS NOT NULL THEN
        RAISE EXCEPTION 'Existing database lacks baseline columns (%). Review additive upgrades manually; baseline seeds will not be replayed.', missing_columns;
    END IF;
END;
$check$;

-- These repository scripts have their own transactions. Keep their existing ordering/semantics.
\ir /db/002-courier.sql
\ir /db/003-merchant-webhook.sql

-- Assert the newly required link before unblocking backend startup.
DO $check$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'parcels' AND column_name = 'merchant_id') THEN
        RAISE EXCEPTION 'Webhook parcel association migration is incomplete';
    END IF;
END;
$check$;

-- Deployment metadata only; application users/parcels/prices/secrets are never overwritten here.
CREATE TABLE IF NOT EXISTS public.swiftdrop_deployment_schema (
    deployment TEXT NOT NULL,
    bundle_sha256 VARCHAR(64) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (deployment, bundle_sha256)
);
INSERT INTO public.swiftdrop_deployment_schema (deployment, bundle_sha256)
VALUES ('swiftdrop', :'expected_bundle')
ON CONFLICT (deployment, bundle_sha256) DO NOTHING;

SELECT pg_advisory_unlock(734510921);
\echo 'SwiftDrop schema bundle is ready.'
