\set ON_ERROR_STOP on
-- The session owns the lock across bootstrap, committed migration steps and the final gate.
SET search_path = public;
SET lock_timeout = '10s';
SET statement_timeout = '5min';
SELECT pg_advisory_lock(734510921);
SELECT set_config('swiftdrop.reviewed_proof_conversion', :'reviewed_proof_conversion', false);

SELECT NOT EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','f')
) AS bootstrap_new_database \gset

\if :bootstrap_new_database
    \echo 'Empty public schema: apply current baseline and starter prices once.'
    BEGIN;
    \ir /db/001-schema.sql
    COMMIT;
\else
    \echo 'Existing schema: never replay baseline seeds, identity updates or approval backfills.'
    \ir /db/preflight.sql
\endif

CREATE TABLE IF NOT EXISTS public.swiftdrop_schema_migrations (
    version VARCHAR(3) PRIMARY KEY,
    checksum VARCHAR(64) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Runner checks immutable per-step checksums and records each successful transaction once.
\ir /db/migrate.sql
-- Read-only current-schema contract: every required column/type/constraint must exist.
-- Future baseline-only changes therefore fail instead of silently recording readiness.
BEGIN READ ONLY;
\ir /db/assert-catalog.sql
COMMIT;

BEGIN;
CREATE TABLE IF NOT EXISTS public.swiftdrop_deployment_schema (
    deployment TEXT NOT NULL,
    bundle_sha256 VARCHAR(64) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (deployment, bundle_sha256)
);
INSERT INTO public.swiftdrop_deployment_schema (deployment,bundle_sha256)
VALUES ('swiftdrop', :'expected_bundle')
ON CONFLICT (deployment,bundle_sha256) DO NOTHING;
COMMIT;
SELECT pg_advisory_unlock(734510921);
\echo 'All migrations and the current catalog gate passed.'
