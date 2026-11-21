# Database upgrade contract

This is a deployment migration package for PostgreSQL 17. **No SQL was executed while
preparing it.** Test on a restored, isolated PostgreSQL database before using real data.
The scripts never delete rows, reset credentials, reassign parcels, recreate storage or
infer merchant approval from an ACTIVE user. Do not edit an applied numbered migration.

## Order and repeatability

`initialize.sql` enables `ON_ERROR_STOP`, takes a session advisory lock, and sets a
10-second lock timeout / five-minute statement timeout. Its order is:

1. Empty public schema: run current `backend/src/main/resources/schema.sql` once inside
   a transaction. Starter zones/prices apply only here. No administrator account is seeded.
2. Existing public schema: skip the entire baseline, including its user/merchant updates
   and seeds. `preflight.sql` verifies the established users/base-parcels catalog before
   changing application tables. Unknown core types/columns/nullability stop for review.
3. Create `swiftdrop_schema_migrations` metadata and run the generated `migrate.sql` plan:
   - **001-additive-schema.sql**: immutable DDL snapshot from commit 5f222a2; creates missing
     module tables and adds known columns. Preserves prices, rows, secrets, business names,
     approval decisions and ownership. New profile columns remain NULL where no authoritative
     values exist. Adds historical proof-chain fields before indexes. Allows NULL customer_id
     for merchant parcels while retaining existing customer links. Secret columns are widened
     only when shorter than 255; larger/text columns are never narrowed. Existing RLS/policies
     are untouched; newly introduced tables receive the baseline's RLS where applicable.
   - **002-proof-user-ids.sql**: requires a single known FK identifying the courier namespace.
     A users.id FK needs no conversion. A couriers.id FK requires explicit reviewed opt-in,
     complete courier/user mappings and stopped legacy writers. It runs a frozen copy of the repository
     delivery-proof migration at 5f222a2, omitting only its outer transaction wrapper so the FK swap,
     ID conversion and version ledger insert are in one transaction. No proof rows are removed.
   - **003-integrity.sql**: frozen contract covering the merged modules. Enforces compatible
     nullability, uniqueness and validated foreign keys, and checks required indexes. Orphans,
     duplicate ownership/keys, NULL required profile/scheduling values and conflicting FKs stop
     the step instead of discarding data or choosing an identity. Compatible constraints are
     reused; unknown FK identity/delete policies are not silently replaced.
4. A **read-only current catalog assertion**, generated from current schema.sql, checks
   19 tables / 184 columns plus their declared foreign keys, uniqueness and indexes. Future
   baseline-only changes cannot silently write a successful marker: add a new migration first.
5. Only after every step and the catalog assertion succeeds, append the exact bundle checksum
   to `swiftdrop_deployment_schema`. Backend init waits for that matching bundle.

Each numbered step and its ledger insert commit together. Successful steps remain committed
if a later step fails; reruns verify their SHA-256 checksums and skip them. A failed step rolls
back and gets no ledger entry. Changed applied files are rejected, including changes to the
frozen proof-conversion body used by step 002. The original repository script is
bundled as a reference only. A later teammate edit does not silently rewrite step 002;
`proofSourceMatchesFrozen=false` flags it for review/new numbered migration. Restore original files and append a new step;
never clear ledgers to force a replay. `--revision` changes the Job identity, not this history.

The old standalone courier/webhook setup scripts are **not replayed** on existing databases;
the reviewed snapshot covers their current DDL. Existing old Kubernetes bundle markers are
retained but do not satisfy a newly rendered bundle's gate.

## Required manual review

Use a backup and a maintenance window. Stop **all backend/worker writers**, including old
versions and external imports, before applying migrations. `Recreate` plus an init gate does
not stop every existing writer early enough to make identity conversion safe on its own.
Keep them stopped until migrations pass and the compatible backend can start.

The following queries are review examples for an operator, not commands executed by this task.
Run only those appropriate to columns/tables actually present:

```sql
-- Missing business identity or approval provenance: supply real, reviewed values.
SELECT id, user_id, business_name, registration_status
FROM public.merchants
WHERE business_name IS NULL OR registration_status IS NULL;

-- Ownership/key collisions: reconcile authoritative records, never delete blindly.
SELECT user_id, count(*) FROM public.merchants GROUP BY user_id HAVING count(*) > 1;
SELECT count(*) FROM (
  SELECT api_key FROM public.merchants WHERE api_key IS NOT NULL
  GROUP BY api_key HAVING count(*) > 1
) AS duplicate_keys;

-- Do not assign today's date to old assignments without reviewing their real schedules.
SELECT id, parcel_id, assigned_at FROM public.courier_assignments WHERE scheduled_date IS NULL;

-- Inspect the actual proof namespace before authorizing conversion.
SELECT c.conname, pg_get_constraintdef(c.oid)
FROM pg_constraint c WHERE c.conrelid='public.delivery_proofs'::regclass AND c.contype='f';
```

Legacy webhook-only merchant rows need real business metadata and an explicit registration
status. Do not derive APPROVED from users.status or fill placeholder names. Preserve existing
APPROVED/PENDING/REJECTED decisions; use PENDING and the normal review process when no decision
exists, after confirming account/profile consistency. The package does not make this decision.
New nullable columns added by step 001 remain available for reviewed corrections if step 003
fails. Existing populated columns are never overwritten. Previously absent timestamp columns
receive migration-time defaults; historical timestamps cannot be reconstructed automatically.

Unknown/missing courier FKs, overlapping ID domains, unmapped users, and courier_assignments
FKs still pointing at profile IDs require a separate reviewed migration. Only the documented
**delivery_proofs** conversion is automated. If old proofs contain multiple initial rows for one
parcel, the new unique-chain index stops rather than deleting history. If necessary, add the
nullable history columns under review and establish the real chain before retrying step 001.
Do not guess a chain or use creation timestamps alone as proof of correction intent.

After backups, identity review and stopping old writers, render the explicit conversion plan:

```powershell
python k8s/prepare.py --backend-image YOUR_BACKEND:IMMUTABLE_TAG --frontend-image YOUR_FRONTEND:IMMUTABLE_TAG --reviewed-proof-conversion --revision reviewed-proof-upgrade
```

This renders locally only; it does not execute SQL. Actual later deployment needs the separate
authorized operator steps in the parent README. The flag changes the Job/bundle identity but
never weakens runtime webhook approval checks. It is false by default.

## Operational limits

- Existing data may need human reconciliation. A blocked migration is safer than a false-ready
  backend. Failures do not justify volume resets or row deletion.
- Type/precision/length mismatches are rejected rather than reinterpreting timestamps, UUIDs,
  report JSON or money. Schedule proper widening/type migrations where justified.
- Index creation/constraint validation can lock or scan large tables; tune the maintenance
  window, timeouts and Job deadline for actual data volume. No load/duration estimate was tested.
- Existing databases are not reseeded. If zones/pricing are missing, configure them through
  approved administration/setup before booking. Provision a trusted administrator separately;
  normal public signup cannot create staff accounts. Do not enable the permissive dev seeder.
- PostgreSQL ownership/privileges, extensions and restored RLS/grants require operator review.
  The current demo backend uses the database owner, not a least-privilege runtime account.
- Application image rollback does not roll back DDL or proof IDs. An older image expecting
  couriers.id may be incompatible after conversion, even if its old bundle marker still exists.
- The SQL lexer and tests are offline contract checks, **not PostgreSQL grammar/execution tests**.
  Verify fresh install, legacy conversion, retry-after-failure and data preservation against
  isolated PostgreSQL backups before a live upgrade.

References: [PostgreSQL 17 ALTER TABLE](https://www.postgresql.org/docs/17/sql-altertable.html)
and [psql error handling/variables](https://www.postgresql.org/docs/17/app-psql.html).
