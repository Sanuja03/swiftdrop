-- REVIEW / APPROVAL REQUIRED before applying to shared Supabase.
-- Deploy backend versions that understand AccountStatus.REJECTED first.
-- Older teammate backends may fail to read rows containing this new enum value.
-- The checked shared database uses a VARCHAR status with no status CHECK constraint.
-- Inspect constraints in other environments before running; do not drop them blindly.
-- Preview the affected accounts before running the UPDATE block.
SELECT u.id, u.status AS current_account_status, m.business_name,
       m.registration_status, 'REJECTED' AS proposed_account_status
FROM public.users u
JOIN public.merchants m ON m.user_id = u.id
WHERE u.role = 'ROLE_MERCHANT'
  AND u.status = 'PENDING_APPROVAL'
  AND m.registration_status = 'REJECTED';

BEGIN;
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '15s';

-- Idempotent. Preserve ACTIVE/SUSPENDED accounts, other roles, and all business data.
-- Do not fabricate business applications for legacy accounts with missing details.
UPDATE public.users u
SET status = 'REJECTED'
FROM public.merchants m
WHERE m.user_id = u.id
  AND u.role = 'ROLE_MERCHANT'
  AND u.status = 'PENDING_APPROVAL'
  AND m.registration_status = 'REJECTED'
RETURNING u.id, u.status;

COMMIT;
