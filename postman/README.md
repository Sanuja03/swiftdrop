# SwiftDrop API tests

Source baseline: merged backend at `2949e47`, branch `feature/janani-final-tasks`. Import
`SwiftDrop_API_Tests.postman_collection.json` and `SwiftDrop_Local.postman_environment.json`.
Use a current Postman Collection Runner or Postman CLI that supports
`pm.execution.skipRequest()`. No CLI, packages, services or databases are installed or started by these assets.

## Coverage and honest results

The collection contains **295 requests**, **145 literal `pm.test` blocks** and
**588 `pm.expect` check sites**, plus the shared HTTP-status assertion. Shared tests
run for each applicable request; conditionals, loops, prerequisite skips and selected
folders mean these static counts are not a claim about executed/passing assertions.
The 97 original requests are retained, with safety, secret handling and execution-order updates.

All **100 production REST method/path variants** extracted from current controllers
have prepared requests. **21 variants have authentication-only coverage**; their
business workflows are not claimed as tested. See [endpoint-coverage.md](endpoint-coverage.md)
for every route and its controller. WebSockets, Actuator framework endpoints and the
single development-only status controller are outside this REST inventory.

Core coverage includes auth/password changes, customer booking/pricing/tracking,
merchant profiles and registration, approval/rejection/reapproval, API-key authentication and
rotation, both merchant order interfaces, sequential/scoped idempotency, cancellation,
both webhook configuration aliases, one-time secrets, explicit secret rotation,
validated test pings, real engine logs and ownership. Additional prepared reads cover
shipments, PDF receipts, notifications, reports, admin catalogs, dispatch and courier lists.

## Safe setup

Use a **dedicated test database and disposable accounts**, never production/shared live data.
Verify the backend datasource yourself before enabling writes: default backend properties
can reference the shared Supabase database. Do not run with the permissive `dev` security
profile; these assertions target the normal security chains. Apply the current merged
schema and reviewed upgrades beforehand; Hibernate does not create the application tables.
Do not reset volumes or replay database setup blindly on existing data.

`baseUrl` is an origin such as `http://localhost:8080`, without `/api`, a path or a trailing
slash. By default only `localhost`, `127.0.0.1` and `[::1]` are permitted. Remote test hosts
require both `allowedTestHost` set to the exact hostname and `testEnvironmentConfirmed=true`.
There is no wildcard/production-host bypass.

All business writes, including negative write tests and the receipt GET that can issue a
receipt row, require **both** `allowWrites=true` and `testEnvironmentConfirmed=true`.
Login/refresh/logout still manage session records; pricing quotes are read-only POSTs.
A skipped request runs no response assertions and **is not a pass**. Console skip messages
contain prerequisite names, not secret values. Keep all opt-in flags false for a first read-only pass.

### Accounts and fixtures

| Inputs | Prerequisite |
|---|---|
| `customerEmail`, `customerPassword` | Verified ACTIVE LOCAL customer; needed for customer/negative-role cases |
| `merchantEmail`, `merchantPassword` | Verified ACTIVE APPROVED merchant profile; own profile supplies its API key dynamically |
| `otherCustomerEmail`, `otherCustomerPassword` | Optional distinct customer for booking isolation |
| `otherMerchantEmail`, `otherMerchantPassword` | Optional distinct approved merchant for real configuration/order/log isolation |
| `operatorEmail`, `operatorPassword` | Optional ACTIVE verified ADMIN or DISPATCHER for engine log operations |
| `adminEmail`, `adminPassword` | ADMIN specifically; a dispatcher cannot approve merchants or read other admin APIs |
| `courierEmail`, `courierPassword` | Optional verified ACTIVE courier for own operational lists |
| `onboardingEmail`, `onboardingPassword` | Fresh reachable test mailbox/password for the merchant lifecycle |
| `onboardingVerificationCode` | Actual unused code from that mailbox; never obtain it through the verification bypass |
| `legacyMerchantEmail`, `legacyMerchantPassword` | Optional seeded verified ACTIVE merchant user **without a profile**, only for legacy registration tests |
| `webhookUrl` | Public ASCII HTTP(S) receiver you control, returning `webhookSuccessStatus` (default 200) |
| `failingWebhookUrl` | Optional controlled public receiver returning `webhookFailureStatus` (default 503) |
| `seededTrackingNumber` | Optional owned customer booking when creation is disabled |
| `newPassword` | Different dedicated-customer password for opt-in change/reset workflows |

Registration passwords must have 8?16 characters, uppercase, lowercase, digit and special
character, without whitespace. The change-password DTO currently accepts 6?100 characters;
use the stronger registration policy anyway. Merchant signup also requires business name,
registration number, phone and business address. Test contact/address values are synthetic.

Active zones/pricing are discovered; merchant tests use discovered city names and a same-zone
1 kg quote to support whichever service type is available. Tokens, merchant IDs, API keys,
tracking numbers, idempotency keys and log IDs come from actual responses; no real user IDs
or existing production records are assumed. Missing zones/credentials/fixtures cause skips.

## Execution order

Run one iteration in folder order. Do not enable destructive/state-changing flags casually.

| Folders | Purpose |
|---|---|
| 00?03 | Initialize/discover pricing, authenticate customer, bookings, public tracking |
| 04 | Authenticate primary merchant; plural configuration validation and saves |
| 05 | Profiles/API keys, portal and machine orders, pricing, replay, ownership, optional cancellations/key rotation |
| 06?08 | Configuration aliases, optional secret rotation/pings, actual engine history and operator log access |
| 09 | Optional staged merchant signup/verification/admin approval, eligibility changes and rejection |
| 10 | Additional customer, admin, reporting, courier and assistant-session contracts |
| 11 | Optional password change, revoked refresh token, new login and password restoration |
| 12 | Disabled known-issue regressions; intentionally expected to fail until backend fixes |
| 13 | Original optional customer registration/email/reset/real Google authentication |
| 14 | Remaining controller routes: anonymous authentication smoke **only** |
| 15?16 | Revoke captured sessions, clear runtime key/secret values, report missing core coverage |

Runtime credentials are captured in `pm.variables` (run-local), not persisted into exported
environments. Run dependent requests together in the Runner; separate manual Send operations
may not retain run-local values. These scopes follow the [Postman variable reference](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-variables/); skipped requests follow the [execution reference](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-execution/). For manual testing, supply tokens in a private local
workspace/environment yourself and clear them before exporting. Persisted non-secret onboarding
IDs/phase markers and a suite-created marker allow the staged inbox flow to resume. Never edit those markers to target an
existing account. Clear them and use a fresh mailbox to start a new lifecycle.

Postman CLI, if already installed:

```text
postman collection run postman/SwiftDrop_API_Tests.postman_collection.json -e postman/SwiftDrop_Local.postman_environment.json
```

Use a private environment copy with credentials. Do not place real passwords or tokens on
command lines or commit populated environment files, response history, Runner reports or
console exports. Committed credential/secret fields and all saved response examples are blank.
Generated signing secrets exist only in the generating response and ephemeral run-local values;
they are never read from GET/profile endpoints or deliberately printed.

### Opt-in flags and side effects

- `allowOrderCancellation`: cancels only parcels captured from this collection's successful
  creation requests. Created parcels remain if cancellation is disabled; there is no delete API.
- `allowApiKeyRotation`: invalidates the primary test merchant's old API key. Its replacement
  is returned/captured and tested; the old key is deliberately tested for 401. Original keys
  cannot be restored, so use a disposable merchant and update its clients afterward.
- `allowSecretRotation`: generates a new signing secret through each alias. Old secrets stop
  working immediately. Run rotation separately if your receiver validates signatures, then
  update that receiver before enabling `allowWebhookSend`.
- `allowWebhookSend`: performs real outbound test pings to your public receivers, creating
  engine log records. A successful HTTP ping alone does **not** prove receiver-side HMAC
  verification; independently verify `t.<rawBody>` with the secret and timestamp tolerance.
- `allowWebhookRetry`: existing DEAD-delivery retry cases can send actual requests. They require
  stable DEAD/non-DEAD fixtures; concurrent workers can invalidate a 409 test fixture. The
  response after a manual retry can be DELIVERED or DEAD. No automatic retry fixtures are fabricated.
- `runMerchantLifecycle`: enables the staged disposable applicant flow below.
- `allowLifecycleRejection`: tests rejected generic status changes, then rejects and explicitly
  reapproves **only the suite-created applicant** through admin merchant review. Rejection revokes
  its old API key; reapproval generates a replacement. The completed flow leaves that applicant
  APPROVED/ACTIVE. Only the merchant profile response captures the replacement key, run-locally.
  If interrupted after rejection, rerun 00 and 09 with the same lifecycle markers to resume
  explicit reapproval. No rows are deleted, and no suspension API is assumed.
- `runLegacyRegistration`: requires the specific profile-less fixture; creates a PENDING profile
  through `/merchant/register`. Normal `/auth/signup` already creates the merchant profile.
- `runPasswordChange`: changes the dedicated customer's password, tests old credentials/refresh
  rejection, then restores its original password. If interrupted, use `newPassword` to log in
  and restore manually. Run this separately from `runPasswordReset`.
- `runRegistration`, `runEmailFlows`, `runPasswordReset`, `runGoogleAuth`: original interactive
  flows; reset changes the password without automatic restoration. SMTP/Google prerequisites apply.
- `runKnownIssues`: enables three intentionally failing security/robustness regressions below.
- `strictCoverage`: final summary fails if machine creation, sequential replay or a successful
  primary-merchant ping did not execute. It is opt-in because those workflows require writes,
  infrastructure and fixture credentials. Individual response assertion failures still fail normally.

### Staged merchant lifecycle (folder 09)

1. Provide administrator credentials and a fresh `onboardingEmail`/`onboardingPassword`.
   Enable test confirmation, writes and `runMerchantLifecycle`. Run 00 and 09 with
   `runEmailFlows=false`. Signup returns PENDING_APPROVAL/no tokens; the admin queue identifies
   the PENDING merchant using the captured signup user ID.
2. Read the mailbox. Set `onboardingVerificationCode` and `runEmailFlows=true`. Run 00 and 09
   again against the **same** private environment. Registration is skipped using persisted
   markers; verification produces no active tokens while pending. Admin approval then activates
   the account; login/profile capture the newly issued API key. No account IDs are entered manually.
3. Supply `webhookUrl` for first configuration. The fresh-profile case requires a newly generated
   secret, while subsequent saves/GET/profile responses must omit it. Optional pings use that
   merchant's actual configuration. Normal saves preserve even legacy inaccessible secrets;
   recovery requires explicit rotation, never silent replacement.
4. Enable `allowLifecycleRejection` only when ready for rejection and explicit reapproval.
   Generic `PATCH /api/v1/admin/users/{userId}/status` requests with SUSPENDED and ACTIVE must
   return **409 ProblemDetail**, with the merchant-review policy message. The denied suspension
   leaves webhook GET and machine API-key access working (200); these checks require the preceding
   409 result. This flow never creates a suspended account or assumes one needs restoring.
5. Admin rejection through `/api/v1/admin/merchants/{merchantId}/reject` returns a REJECTED
   profile with no exposed credentials. The old JWT cannot access webhook settings (403), the
   revoked key is unauthorized (401), and password login is refused (403).
6. The existing admin `/{merchantId}/approve` endpoint explicitly reapproves that same rejected
   applicant (200). Admin responses still omit usable credentials. Fresh password login must
   show ACTIVE/verified, and the merchant's own profile must return a replacement API key.
   The suite checks that this key differs from the pre-rejection key, the old key remains 401,
   the replacement authenticates machine reads (200), webhook configuration remains private
   and unchanged, and a second approval is 409. The resulting persisted phase is `reapproved`.

Run the rejection/reapproval segment together to verify old-versus-new keys and configuration
preservation: old keys and snapshots are never persisted in the environment. On an interrupted
run, explicit reapproval/login/profile can resume, but unavailable old-key comparisons and
snapshot checks skip. Do not interpret those skips as successful receiver or key-replacement
verification. Keep the suite-created account/profile rows for review.

On later resumes, phase guards skip completed signup/verification/first-configuration operations.
Do not rerun verification with an already used code. For clean results select the documented
folders; enabling `runEmailFlows` for the whole collection can also send original customer-flow emails.

## Infrastructure and API limitations

- Database and normal JWT configuration are required. Customer bookings do not automatically
  become merchant-owned parcels; the new merchant machine/portal creation cases establish ownership.
- RabbitMQ/outbox workers are required for **event-driven** delivery and retries. One-shot test
  pings use the shared signer/HTTP client and record synchronously in `webhook_deliveries`.
  Both history route families read those engine records. Existing legacy log rows are not deleted.
- Private/internal/localhost callback destinations are rejected; there is no development bypass.
  DNS and a controlled public receiver are needed. DNS-rebinding risk remains between validation
  and connection; production needs outbound network enforcement.
- SMTP is needed for verification/reset emails. Real Google ID tokens and matching configured
  client IDs are needed for Google authentication; no mock token shortcut is invented.
- Full positive dispatch, courier proof/photo, catalog editing and report snapshot creation are
  not claimed. Their missing scenarios are identified as authentication-only in the coverage map;
  positive execution needs coordinated parcel/assignment states and, for photos, MinIO/bucket/file fixtures.
- Idempotency currently works through an in-memory per-process cache. It ignores changed bodies,
  is not atomic for concurrent creates and does not survive restarts or coordinate replicas.
  The normal suite verifies sequential replay and merchant scoping, not durable idempotency.
- Known regression probes expect safer behavior: already-verified email must reject arbitrary
  codes (current source issues tokens), changed-body idempotency must conflict (current source
  replays 200), and malformed merchant JSON should be 400 (generic exception handling suggests 500).
  These are source findings, not live observations from this task. Do not normalize them as passes.
- Logout/password changes revoke refresh tokens; immediate revocation of issued JWTs is not asserted.

## Offline validation versus live HTTP

Run from the repository root with Node already installed:

```text
node postman/validate.mjs
node postman/validate.mjs --write-coverage
```

The validator uses only Node built-ins. It parses both JSON files, compiles 435 scripts,
checks every request method/path against actual Java controller annotations/constants,
checks variable references/secret defaults/core DTO property names, simulates default and
opted-in pre-request branches, checks write/host guards and exercises quoted/backslashed
password serialization. Eleven focused merchant lifecycle prerequisite/status-contract cases
also cover the merged 409 policy, rejection/reapproval phases, ownership markers, and missing
credential guards. These use synthetic inputs, not HTTP responses.
`--write-coverage` regenerates only the Markdown coverage map.
This is not official Postman JSON Schema validation, the full Postman runtime, response-assertion
execution, receiver signature verification, Kubernetes validation or a live API test run.

At preparation time no listener was found on local port 8080 and no Postman/Newman CLI was
available. **Zero live HTTP requests were sent to SwiftDrop.** No services were started, dependencies
installed, databases modified, accounts created or keys/secrets rotated by this task.
