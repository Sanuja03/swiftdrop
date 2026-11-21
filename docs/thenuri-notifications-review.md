# Thenuri contribution: setup, verification and PR notes

## Current review — 19 November 2026

Branch: feature/admin-reports-notifications. HEAD remains e5ab651. Updated dev
8e72daf (including Indu's dispatch/assistant, courier work and Hasindu's merchant
API, PR #7) is integrated in the working tree. MERGE_HEAD is 8e72daf. Conflicts
are resolved; this merge and Thenuri's feature work remain UNCOMMITTED. No push
or PR has been made. Preserve the existing stash and backups until user approval.

The index contains incoming dev plus earlier staged feature versions. Several new
notification/report files are still untracked. Do not commit only the current
index: stage every reviewed final feature file when committing is authorized.

## Automated verification

- Backend mvnw -B verify: 450 tests, zero failures/errors, five Docker-dependent
  tests skipped. Packaging and coverage report generation passed.
- Frontend npm test: 29 Node tests and 62 Vitest component tests passed.
- Frontend lint and build passed. Existing warnings: VerifyEmailPage effect
  dependencies and the large frontend bundle.
- Merchant-alert tests exercise registration -> transactional outbox -> consumer
  -> real H2 notification persistence, recipient roles, unread/read behavior,
  redelivery and concurrent duplicate protection. Mail is mocked; these alerts
  are in-app only. The integration test calls the consumer directly, so it does
  not prove live RabbitMQ delivery.
- Security tests cover both operations roles for merchant review/approve/reject,
  and deny customer/merchant access. The approving identity comes from the
  authenticated principal in Hasindu's existing controller.
- UI tests cover the notification link opening the Merchant Approvals tab for
  both roles, read/unread handling, and customer-specific empty-state wording.

## Changes beyond the existing Thenuri features

- Routing preserves dev's customer, courier, merchant, dispatch and assistant
  features, and adds Thenuri's /admin/users, /admin/reports and /notifications.
  ADMIN and DISPATCHER retain the shared /admin landing; the new courier landing
  from dev is preserved. AdminLayout uses the shared sidebar/topbar with the
  ADMIN_NAVIGATION entries requested by the leader. Dispatch retains Indu's shell.
- MerchantAdminController now permits ADMIN or DISPATCHER, matching the existing
  shared operations boundary. No user role or account was changed for this.
- MerchantService.registerMerchant adds one outbox publication in the registration
  transaction. The payload contains the merchant ID only, never keys/secrets.
- The notifications module owns a separate durable queue,
  swiftdrop.notifications.merchants, bound to merchant.registered. Sanjalee's
  messaging/webhook code is unchanged. The consumer alerts active ADMIN and
  DISPATCHER users only while the application remains PENDING. It uses stable
  per-event/per-recipient UUIDs with the existing unique event_id constraint.
- Alerts link to /admin/users?tab=merchants. User Management honors that link.
  The shared bell refreshes on page/window focus and notification read/refresh
  actions. No new WebSocket/push mechanism or historical alert backfill was added.
- DemoMerchantSeeder has an opt-out (app.dev.seed-merchant=false) so local testing
  can preserve the shared database. It is disabled in the ignored local .env.
  Its normal default dev behavior remains unchanged for teammates.

## Live runtime and database

Wi-Fi startup timed out during the Supabase connection handshake. After switching
to hotspot, the backend started and connected to the existing transaction pool.
Frontend is running at http://localhost:5173; protected merchant API returns 401
before login. The backend uses local-review profile, SQL initialization disabled,
and demo merchant seeding disabled. Existing auth initialization still runs.

The first signed-in merchant listing failed because public.merchants did not
exist. A read-only schema check confirmed this exact cause. Thenuri approved
applying Hasindu's merchant setup from dev. On 19 November it was applied in one
transaction with a short lock timeout and a guard that stops if a teammate has
created the merchant table meanwhile. It created merchants and
merchant_webhook_logs with indexes and added parcels.merchant_id with its index.
No existing records were deleted or updated. A read-only check then confirmed
all merchant entity columns and existing outbox/notifications tables are present.
There were no merchant applications immediately after setup. Thenuri refreshed the signed-in Merchant Approvals screen and confirmed it shows "No merchant applications" without the previous error.

The exact executed SQL is retained outside Git in the review artifacts as
merchant-setup-from-dev.sql. The source schema is already in dev at
backend/src/main/resources/schema.sql. Do not rerun the whole shared schema or
apply other merchant/courier scripts without reviewing their need and approval.

RabbitMQ was unavailable because the existing Ubuntu VM was powered off. The VM
was started headlessly without changing its network settings. Its configured NAT
forwards are 5672 and 15672. After Thenuri started swiftdrop-rabbitmq inside Ubuntu, the backend connected and health became UP. A live new-merchant alert and real approve/reject test need a dedicated
authorized test application; do not approve/reject someone else's application.

## Merchant onboarding connection — fixed locally with Thenuri's authorization

On 19 November Thenuri explicitly authorized fixing the cross-module signup gap.
Signup now accepts separate business name, optional registration number, and
required business address/contact phone. AuthService registers the pending user,
pending merchant application and existing registration outbox event atomically.
Customer signup is preserved. Merchant signup returns no usable session tokens.
Approval activates the linked merchant user without changing email verification;
rejection leaves the account restricted. Approval cannot reactivate suspended
accounts, and only pending applications can be reviewed. Conflicts return HTTP409.
The merchant landing page is now /merchant for approved, verified users.

Email verification and its repeat cannot issue tokens to inactive accounts.
Pending accounts cannot bypass approval through Google sign-in or refresh tokens.
The API-key filter also requires an active, email-verified merchant user as well
as an approved application. Existing records are not backfilled or activated by
startup; the fix applies to future signups and explicitly reviewed applications.
No additional schema change was needed for this fix.

The full backend run passed 459 tests (five Docker skips); its packaging initially
failed because the previous backend process locked the JAR. After stopping that
process, the final affected auth/security/onboarding verification passed 46 tests
and packaging succeeded. That focused run includes three further regression tests.
Eight H2 onboarding integration tests cover both verification/approval orderings,
rejection, suspended accounts, duplicate review, application/outbox persistence,
and incomplete signup with no orphan account. Email is mocked in these tests.
Frontend: 30 Node tests and 65 component tests covered; one new signup test hit the
five-second harness timeout during concurrent VM/backend load, then all three signup
tests passed with a 15-second allowance for that test. Lint/build passed. No code
change was needed to resolve that timing issue. Existing warnings remain.

Thenuri supplied and confirmed permission for two separate test inboxes. Two clearly
marked test merchant applications were created through the real signup API: TEST
ONLY - Thenuri Approval Check and TEST ONLY - Thenuri Rejection Check. Both returned
PENDING_APPROVAL with no login tokens. Read-only verification confirmed both merchant
applications are PENDING, both registration outbox events are PUBLISHED, and each
produced exactly one notification for the one active operations account (1/1 each).
This verifies live registration -> outbox -> RabbitMQ -> persisted admin alert.
Private generated passwords are outside the repository and are never logged.
Thenuri verified both inboxes. Read-only checks confirmed emailVerified=true with both applications still PENDING and accounts PENDING_APPROVAL. Login for both verified-but-pending accounts correctly returned HTTP403. The live approval/login and retained-pending/login checks are complete as recorded below. UI alert read/unread review remains; live rejection is deferred by Thenuri. No existing teammate application or account was changed. The updated
backend and RabbitMQ are healthy (UP).
### Live approval and pending-account check — 19 November

Thenuri manually approved TEST ONLY - Thenuri Approval Check. Read-only checks
confirmed its merchant application is APPROVED, linked user is ACTIVE, and email
is verified. Normal login with that test account's own credentials returned HTTP200
with a usable ROLE_MERCHANT token; reading its own merchant profile returned HTTP200
and APPROVED. Passwords, tokens and API keys were not printed.

Thenuri explicitly requested leaving TEST ONLY - Thenuri Rejection Check pending
instead of rejecting it. It remains PENDING, with user PENDING_APPROVAL and verified
email. Normal login returned HTTP403 as required. No rejection was submitted; do
not reject this application without a new user instruction. A live rejection check
is deferred at Thenuri's request; rejection remains covered by the isolated tests.

## Previously completed manual review

Thenuri confirmed CSV/PDF downloads, User Management filters, saved reports, and
customer notification read/unread behavior. Desktop/mobile report layouts were
inspected. SMTP authentication and actual verification email receipt were
confirmed on 18 November with privately entered credentials. Verification-only
email wording is not part of regular parcel status emails. No fresh email was
sent during this integration.

Success & Failure independently supports All Time / Last 7 Days / Last 30 Days.
Daily Deliveries independently supports 7/30 days. Saved Reports filters by when
copies were saved; it does not change the reporting period stored in a copy.
Overview cards remain overall. Saved copies preserve frozen values and outcome
periods; old snapshots retain their original overall outcome meaning.

## Private configuration and migrations

Private values live in ignored backend/.env and are excluded from Git/Docker.
Use SUPABASE_DB_PASSWORD for the existing JDBC configuration and
SPRING_MAIL_USERNAME/SPRING_MAIL_PASSWORD for SMTP. Never put values in PRs/logs.
The local transaction-pool URL uses sslmode=require&prepareThreshold=0 and a small
Hikari pool. It does not change tracked datasource settings or teammate sessions.

Include backend/database/notifications.sql and report_snapshots.sql in the eventual
PR; both were applied to the current shared project with earlier approval. New
environments need owner review and application of these scripts. The merchant
alert reuses the existing notification/outbox tables and needs no additional
feature-specific migration. Another literal credential scan found zero matches
in changed/new files; backend/.env is absent from the Git index.

## Before committing

Finish the live Merchant Approvals refresh and dedicated test application checks,
verify live broker notification delivery if the broker can be started, and let
Thenuri review the final screens. Re-fetch dev immediately before the PR. Review
the full working tree AND untracked files; include all final feature files when
staging. Check staged differences against updated dev, not just the old HEAD.
Only commit/push or create a PR after Thenuri explicitly authorizes those steps.

## Rejection reasons and merchant evidence — 19 November

Thenuri requested rejection reasons and reason emails, then requested preset
choices. The Reject dialog now requires a choice: incomplete business information,
inconsistent business details, unverifiable contact details, duplicate application,
or Other. Presets accept optional additional details; Other requires an explanation.
The combined text is limited to 2000 characters, stored in existing approval_notes,
returned in the application detail DTO and included verbatim in the decision email.
Backend rejection also rejects null, blank or overlong reasons; approval notes
remain optional. Existing records and schema definitions are unchanged.

A MERCHANT_REJECTED event is persisted in the existing outbox in the same transaction
as the decision. A separate notifications-owned queue sends the applicant a plain
text email through the shared SMTP sender and records a decision notification after
SMTP acceptance. Completed events are deduplicated. Failed sends are retained in
swiftdrop.notifications.merchant-rejections.dlq using the team's existing fail-to-DLQ
listener policy; recovery/replay is manual, not an automatic retry schedule. As with
other SMTP/outbox workflows, a crash after SMTP acceptance but before the completion
record can cause a duplicate email on replay. No claim of exactly-once mail delivery.
No teammate messaging code or existing queue arguments were changed.

The live verified pending test application must remain pending at Thenuri's request.
No live rejection or rejection email is authorized for that application. New decision
content and persistence are tested with mocked SMTP and isolated H2 fixtures.

Merchant signup currently collects business name, contact person, phone, business
address, email and optional business registration number. No certificate upload,
registry lookup or automatic certification exists. Thenuri asked to keep business
evidence requirements separate from the rejection reason feature; the registration
number remains optional. If the team requires proof of registration, agree on a
required registration number plus a private certificate upload/admin preview, with
file size/type validation and ownership/access controls. A typed number alone is
not proof that the business was checked. Do not add this storage/schema workflow
without agreeing the separate scope and obtaining shared database approval if needed.

Rejection feature validation: full backend test run passed 469 tests with five
Docker-dependent skips. The final SMTP delivery unit run passed seven tests and
packaging succeeded. Seven focused UI tests passed; final lint/build and whitespace
checks passed. No live rejection was submitted and no shared migration was needed.

## Approval/rejection communication and required BR number — current update

Thenuri requested BOTH email and in-app notices for approval and rejection, and
explicitly required a business registration number. Merchant signup validation
and /merchant/register now require a nonblank BR number (up to 100 characters);
both forms mark it required. Customer signup remains unchanged. No shared schema
change is needed, and old profiles are not backfilled or edited automatically.
Certificate/document upload and actual registry checks remain separate requirements
to agree with Hasindu (merchant module), Risinu (signup/authentication), and the leader.

Approval now records MERCHANT_APPROVED in the existing outbox in the same transaction
as activating the eligible linked user. It never marks an unverified email verified.
Both decision types feed independent in-app and email channels. A durable decisions
queue creates idempotent notifications keyed by domain event ID; SMTP failures cannot
prevent that in-app record. Approval and rejection email queues use the existing
fail-to-DLQ factory and their own DLQs. Email delivery has at-least-once semantics;
replaying or redelivering a message may repeat an email. There is no persistent
email completion receipt, so do not claim exactly-once delivery. This supersedes
the earlier combined rejection consumer/completion-marker description above.

The merchant portal now has its own Notifications tab. Rejected/pending accounts
remain unable to log in as requested; a stored in-app rejection notice does not
grant access to merchant services. The email communicates the decision while the
account remains restricted. Approval/rejection messages contain no API credentials.

The existing approved test account was approved before an approval event existed.
A one-time repair helper was prepared outside Git to queue its missing event only
if that exact test business, recipient and APPROVED/ACTIVE/verified states match.
It leaves users/merchants unchanged and refuses duplicate approval events. Do not
reject or otherwise change the second verified pending test application.

Communication update validation: 474 backend tests passed, five Docker-dependent
tests skipped. Seven focused frontend tests, lint, build and packaging passed.
Initial parallel Vitest workers timed out before starting; a one-worker rerun
passed. Backend health is UP. The scoped missing-notice helper queued exactly one
approval event for the already approved test account without changing its status.
Normal login and its own notifications API confirmed one unread MERCHANT_APPROVED
notice. SMTP accepted the corresponding approval email; Thenuri confirmed it arrived in the recipient inbox. The second verified test application remains pending and is not rejected.
No new schema migration, commit, push or PR was performed for this update.

## Later rejection of an approved merchant — 19 November

Thenuri requested that ADMIN and DISPATCHER may reject a previously approved
merchant. Reject is now shown for PENDING and APPROVED applications; approval
remains pending-only, and already-rejected applications cannot be rejected again.
The existing mandatory reason and rejection outbox/email/in-app channels are reused.

Rejection changes the linked account to PENDING_APPROVAL unless it was already
SUSPENDED, and clears its API key. Existing refresh tokens cannot renew an inactive
account. Merchant portal authorization now checks the current user's active,
email-verified merchant status and current approved profile on every merchant
request, so an old JWT cannot continue using merchant services after rejection.
An active legacy merchant user without a profile may still reach profile setup;
a pending/rejected profile cannot bypass this check by restoring user status alone.

Focused regression checks passed: 38 backend tests (merchant security, admin service,
real H2 onboarding), and 8 UI tests. Tests cover approved -> rejected, login and
refresh denial, cleared API key, stale-session denial, manual status-restore bypass,
and the approved-row rejection control. No live account was rejected for this test;
the original approved and pending test applications remain unchanged.

## Local runtime after laptop reboot — 19 November

Frontend was restarted on 127.0.0.1:5173 and the existing Ubuntu VM was started
headlessly without changing its configuration. The configured Supabase transaction
pool on 6543 timed out/closed during SSL negotiation. An encrypted read-only SELECT 1
probe against the same project on session-pool port 5432 succeeded. Only ignored
backend/.env was changed to use port 5432; maximum Hikari connections remains two,
minimum idle zero, SSL required and prepared-statement threshold zero retained.
No teammate session was terminated and no schema/data repair was performed.

The packaged backend then started on 8080. Frontend returned HTTP200, protected
merchant API through the proxy returned expected HTTP401 before authentication,
approved test merchant normal login/profile succeeded and the pending test login
returned HTTP403. Global health remains HTTP503 until the existing
swiftdrop-rabbitmq container is started inside Ubuntu. Thenuri has been asked to run
sudo docker start swiftdrop-rabbitmq privately; no VM password is requested in chat.

After Thenuri started the existing RabbitMQ container, the backend connected and
global health returned UP. Final frontend HTTP200 and protected merchant proxy
HTTP401 were verified. Both application services and the broker are ready locally.

## Rejected merchant login wording — 19 November

Thenuri manually tested a rejected account and reported the old "under verification"
message. AuthService now checks the linked merchant decision after validating the
password, distinguishing REJECTED from a genuinely pending application even though
both use the login-blocking user status PENDING_APPROVAL. Rejected users are told:
"Your merchant application has been rejected. Please check your email for the
rejection reason or contact SwiftDrop support." Pending users are told that admin
approval is pending; the former 24-hour activation promise is removed. Google login
uses the same decision distinction. Suspended/approved/customer behavior and access
restrictions are unchanged. Rejected status takes precedence over email-verification
instructions after a correct password, so verification is not presented as a way
to undo rejection. No account decision or rejection reason is changed by this fix.

Focused auth and real H2 onboarding tests passed: 35 tests, zero failures/errors.
Cases include rejected verified/unverified users, genuine pending messages and
no token issuance. Backend packaging passed. No frontend source change is required:
the login page already displays the backend's error message. Do not reset a manually
rejected test account to pending to match an earlier test description in this file.

The corrected backend was restarted successfully; health is UP. The known approved
test login still returns HTTP200, and the known pending test login returns HTTP403
with the new pending-admin-approval wording. A read-only aggregate check confirmed
rejected applications exist in the connected database. No live account status was
changed; the user's separately tested rejected account should be retried privately.

## Complete admin navigation and Zones/Pricing permissions — 19 November

Latest dev 4ad65e0 (PR #8, feature/booking-and-pricing) was integrated locally after
a 263-file backup. The pending merge target is now 4ad65e0; HEAD remains e5ab651,
and no commit/push/PR was made. One shared security-test conflict was resolved by
keeping that test exactly from dev. Teammates' Zones/Pricing pages, APIs, controllers,
services, CSS and original tests remain byte-equivalent in Git to current dev.
No shared SQL was applied. The incoming schema.sql change only preserves existing
zone/price seed records on conflict instead of overwriting administrator edits.

Thenuri explicitly chose to keep dispatcher access denied for Zones and Pricing.
This supersedes earlier notes describing every admin activity as shared. The main
security configuration now has specific ADMIN-only zone/pricing URL boundaries
before the broader ADMIN/DISPATCHER boundary. Other agreed operations permissions
are unchanged. Integration-owned frontend routes also restrict these two screens
to ADMIN and hide their menu links from DISPATCHER accounts.

The admin sidebar now connects User Management (including Merchant Approvals),
Reports, Zones, Pricing, Dispatch Queue, Courier Workloads and Notifications.
Dispatchers see the same menu except Zones/Pricing. The original /dispatch routes
and Indu's own dispatcher shell/sidebar remain unchanged at Thenuri's request;
no duplicate dispatcher implementation was built. Admin landing remains Reports.
Shared sidebar styles already support scrolling and mobile navigation.

Integration UI tests render the existing team Zones/Pricing pages with mocked reads,
verify menu destinations, and test dispatcher direct-URL denial and link filtering.
Separate integration-owned backend tests cover denied dispatcher zone/pricing read
and write requests without modifying the team's original tests. Do not edit teammate
implementations to unify their dispatcher shell without agreeing that scope.

Final navigation validation: 515 backend tests passed with five Docker-dependent
skips, including the team's unchanged dispatcher access-denied test. Fourteen
focused UI tests passed; lint, production build, backend packaging and whitespace
checks passed. No teammate page implementation or original test was rewritten.

The latest backend package built successfully, but its local restart is waiting on
a Supabase SSL-handshake connectivity timeout. A 15-second read-only probe also
timed out. Thenuri has been asked to confirm the working hotspot/mobile data.
Do not claim the backend review environment is ready until health is UP again.

After Thenuri reconnected the working hotspot, the final integrated backend started
successfully. Database and RabbitMQ connected; health is UP and frontend HTTP200
was verified. This resolves the preceding runtime connectivity blocker.

### Shared navigation on dispatch pages (latest review)

At Thenuri's request, the integration-owned App.jsx now wraps /dispatch and
/dispatch/workload in the shared AdminLayout. This supersedes the earlier note
about retaining a separate dispatcher shell on those routes. Both operations roles
can navigate back to Users, Reports and Notifications from dispatch pages. Zones
and Pricing remain ADMIN-only. The existing DispatcherRoute guard, page components,
API calls and URLs are retained; Indu's dispatch feature files and tests are unchanged.

Validation: all seven shared navigation tests passed. Across these and the four
existing dispatch test files, 41 tests passed and four failed. The four failures
are existing route assertions expecting the previous dispatcher navigation, ADMIN
badge or Customer View link. Those teammate-owned assertions still need agreement
and alignment before the PR; the full selected suite is not green. Lint and the
production build passed. No commit, push or PR was made for this change.

### Final pre-commit audit (19 November 2026)

Thenuri confirmed the final sidebar/pagination appearance and rejection email reason.
At her request to finish the remaining checks, the four old dispatch route-test
expectations were aligned with the shared shell; only that test file in the dispatch
feature differs from dev. This supersedes the preceding unresolved-test note.
Dispatch implementation and standalone layout tests remain unchanged.

The diff audit restored dev's courier/merchant frontend role guards and the merchant
route constant, and reduced unnecessary route-file reordering. Three regression
checks verify unauthorized customer access to courier and merchant pages is denied.
Final results: 111 frontend tests pass, lint/build pass; backend reports 515 tests,
zero failures/errors and five Docker-dependent skips. The scoped credential scan
found no matches. Final whitespace checks against dev pass.

Fetched dev still equals MERGE_HEAD 4ad65e0deacfab7a7b352db2f7b6c97152850857;
there are no unresolved Git merge entries. The merge is still uncommitted and the
index contains older staged versions; stage the reviewed final content when a
commit is authorized. No push or PR was performed. See thenuri-team-impact-review.md
for teammate integration changes and database setup history.

### Latest role decision: original dispatcher layout, integrated admin layout

Thenuri requested retaining Indu's dispatcher experience. Dispatchers now land at
/dispatch and use the unchanged original DispatcherLayout/sidebar/topbar. Admins
use the integrated AdminLayout even on dispatch routes. This supersedes the prior
shared-sidebar-for-both-roles notes. Indu's dispatch implementation files remain
unchanged; route integration test assertions reflect the role-specific layout.

User management, merchant review, reports/snapshots, Zones and Pricing are now
ADMIN-only in both UI routes and backend authorization. Dispatch functionality
and own notifications remain accessible to dispatchers. The existing operational
webhook-log permission for dispatchers is preserved. New application alerts are
admin-only; historical notifications are not altered. Both registration forms
remain, per the team's decision. No commit/push/PR was performed during this change.
