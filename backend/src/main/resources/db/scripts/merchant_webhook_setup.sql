-- Apply manually AFTER schema.sql, before deploying the webhook configuration API.
-- Additive: does not create merchant accounts for existing users or reassign parcels.
-- If an existing merchant table has duplicate user_id values, the unique index fails
-- and this transaction rolls back. Resolve ownership explicitly; do not delete data.
BEGIN;

CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    webhook_url VARCHAR(500),
    webhook_secret VARCHAR(128)
);

-- Accommodate an already-created merchant table using the same id/user_id contract.
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS webhook_url VARCHAR(500);
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS webhook_secret VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS uq_merchants_webhook_user ON merchants(user_id);

-- Existing rows stay NULL; existing customer booking behavior is unchanged.
ALTER TABLE parcels ADD COLUMN IF NOT EXISTS merchant_id UUID REFERENCES merchants(id);
CREATE INDEX IF NOT EXISTS idx_parcels_merchant_id ON parcels(merchant_id);

COMMIT;
