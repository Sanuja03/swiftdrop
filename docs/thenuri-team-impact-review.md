# Thenuri: changes affecting team integrations

Reviewed against fetched `origin/dev` at
`4ad65e0deacfab7a7b352db2f7b6c97152850857` on 19 November 2026.
No commit, push or PR was performed. The previous dev merge remains uncommitted.

The later request to remove the business address was withdrawn. Those address-only
edits were reverted; signup and merchant validation remain as before that request.

## Latest agreed role/layout adjustment

This section supersedes earlier shared-role descriptions below. Both merchant
registration forms are retained for now, as Thenuri confirmed with the team.
Dispatchers land at /dispatch and use Indu's original DispatcherLayout, sidebar
and topbar, without modifications to those implementation files. Admins retain
the integrated AdminLayout, including when visiting Dispatch Queue/Workloads.
Dispatcher notifications also retain the dispatcher layout when opened directly.

Users, merchant approvals/rejections, reports/snapshots, Zones and Pricing are
ADMIN-only in frontend routes and backend authorization. New merchant-application
alerts go only to active admins. Existing notification records are not deleted.
Dispatcher APIs remain available to both operations roles. The team's pre-existing
dispatcher access to /api/v1/admin/webhooks/** is preserved; this is an operational
exception, not general admin access. No new account/parcel/database data changes
were made for this adjustment. Signup/auth registration logic is unchanged here;
only the frontend post-login dispatcher destination changed.

Role-split validation: 114 frontend tests passed, plus lint/build. The full backend
run executed 516 tests with five skips and one old dispatcher-approval expectation
failing; after correcting that expectation, all 19 MerchantSecurityTest cases
passed on the focused rerun. All other cases in the full run had passed. Backend
packaging passed. This is full-run evidence plus the corrected focused rerun,
not a claim of a second all-green full run. No commit/push/PR was performed.

Known separate inherited issue: public signup accepts its requested Role without
an allowlist for CUSTOMER/MERCHANT. This is also present in fetched dev. It was
reported for team coordination and was not silently changed in this layout task.

## Conflict and scope checks

The fetched dev revision equals the current MERGE_HEAD. There are no unmerged
index entries or conflict markers found in the reviewed changes. No newer dev
revision needs merging at this check. This is a check of the current local result,
not a guarantee against future changes on dev. The index still contains earlier
staged versions and incoming dev files; do not commit only the existing index.
Stage the complete reviewed result when Thenuri authorizes a commit.

Compare the final files against dev, rather than treating all incoming merge files
shown in git status as changes authored by Thenuri.

## Existing team code changed

| Area | Files and resulting change | Coordination / effect |
| --- | --- | --- |
| Authentication / signup | `SignUpRequest.java`, `AuthService.java`, `SignupPage.jsx`: merchant signup also creates the pending application; merchant-only business details and registration number are required. Login distinguishes pending/rejected decisions; Google login, refresh and repeated verification cannot issue tokens for inactive accounts. | Discuss with Risinu and Hasindu. Customer signup fields remain unchanged. Merchant clients must supply the required details. The unsupported 24-hour approval promise was removed. |
| User permissions | `UserController.java`, `SecurityConfig.java`: both ADMIN and DISPATCHER can manage users, merchant applications and reports. Explicit ADMIN-only Zones/Pricing rules precede the general operations rule. | Agreed role behaviour. Zones/Pricing implementation and original security test are unchanged. The broader admin matcher also applies to future admin endpoints unless a more specific restriction is added. |
| Merchant admin API | `MerchantAdminController.java`: permits both operations roles. `MerchantProfileResponse.java` exposes review notes. `MerchantExceptionHandler.java` preserves intended conflict statuses. | Hasindu's existing endpoints are retained. No API keys are exposed through admin responses. |
| Merchant registration | `MerchantRegisterRequest.java`, `MerchantService.java`: require a registration number, include review notes in mapped profiles, and publish registration events using the existing outbox. | Hasindu integration. No certificate upload or automatic authenticity check was added. |
| Merchant decisions | `MerchantAdminService.java`: approval activates the linked account without bypassing email verification; rejection requires a reason, can apply to pending or approved merchants, clears the API key and blocks account access. Both decisions publish notification events. Suspended accounts remain protected. | Intentional behaviour changes requested by Thenuri. Previously approved merchants can lose access following rejection. |
| Merchant security | `MerchantApiKeyAuthFilter.java`, `MerchantSecurityConfig.java`: check current linked account status/email verification and merchant approval, including requests using previously issued credentials. | Hasindu/Risinu integration needed to enforce revocation. Existing portal/API URLs remain. |
| Merchant screen | `MerchantPortal.jsx`: adds a Notifications tab and requires the registration number in its existing setup form. | Existing dashboard, order, key and webhook implementations remain. |
| Shared navigation | `App.jsx`, `AdminLayout.jsx`, `navigation.js`, `routes.js`, `session.js`: common operations landing/menu, Users/Reports/Notifications routes, existing dispatch pages hosted inside AdminLayout. | Indu's dispatch UI now uses the shared shell when routed through App. Existing dispatch URLs, guard, assignment logic and standalone layout components remain. Dispatchers land at `/admin` and cannot access Zones/Pricing. Existing courier/merchant role guards and merchant route constant are preserved. |
| Shared auth/loading | `ProtectedRoute.jsx`: shared spinner and explicit access-denied message; accepts role names with or without ROLE_ prefix. | Unauthorized roles see a denial message instead of the former home-page redirect. Backend authorization is still required. |
| Shared notification bell | `Topbar.jsx`, `Topbar.module.css`: bell opens notifications and shows unread count; refreshes on focus and notification updates. | Affects layouts using this shared topbar, including customer screens; existing standalone dispatcher topbar is untouched. |
| Runtime/config | `DemoMerchantSeeder.java`: opt-out property, default unchanged. `.env.example`, `docker-compose.yml`: document/pass SMTP environment settings. `backend/pom.xml`: explicit JDBC starter for report persistence. `frontend/package.json` / lockfile: PDF dependency and test command covering shared-client tests. | Sender credentials remain in ignored local `.env`, not source. Shared database review uses seed opt-out. No dependency-version sweep was performed. |
| Existing tests | `DemoMerchantSeederTest.java`, `RoleAccessSecurityTest.java`, `AuthServiceTest.java`, `MerchantControllerTest.java`, `MerchantSecurityTest.java`, `MerchantAdminServiceTest.java`, `MerchantServiceTest.java`: adapted constructors/fixtures and coverage for the intentional integration changes. | Test changes accompany corresponding behaviour; five Docker-dependent cases remain skipped locally. |
| Dispatch route tests | `DispatchIntegration.component.test.jsx`: four assertions updated for shared navigation and added notification-count mock. | Only this test file inside Indu's dispatch feature differs from dev. Dispatch API, assignment, workload and customer route checks remain. Original standalone dispatcher-layout tests still run. |

## Added contribution code and database effects

Reports include live summary/outcome/daily queries, independent report periods,
CSV/PDF exports, saved historical report snapshots and five-row snapshot paging.
User Management includes filtering, account access controls and merchant-review UI.
Notifications include per-user read/unread APIs/UI, parcel status delivery, operations
alerts for new applications, and approval/rejection email plus in-app delivery.
New merchant event payloads connect these features to the existing team outbox.

The original messaging/RabbitMQ implementation is unchanged. New notification
queues/bindings and decision email dead-letter queues use that infrastructure.
Decision email delivery is at least once; replay can produce duplicate email.

Previously approved database setup created notifications and report_snapshots.
Hasindu's approved merchant setup created merchants and merchant_webhook_logs and
added parcels.merchant_id with indexes. These were shared-database changes, not
changes confined to Git. No database migrations or live account/parcel mutations
were performed in this final pre-commit review. Existing authorized live tests and
manual decisions were not reset. The snapshot SQL enables RLS on its own table.

No source differences from dev were found in Zones, Pricing, courier, AI assistant,
or the existing messaging modules. Incoming dev files in those areas were preserved.

## Final checks

Full backend test run: 515 tests, zero failures/errors, five Docker-dependent skips.
Frontend full suite: 111 passing tests (30 Node tests and 81 component tests).
Lint and production build passed. Known lint warning:
VerifyEmailPage hook dependencies. Known build warning: large JavaScript chunk.
Neither warning was introduced by the final navigation/pagination review.

Credential check compared final working files and their staged versions against
configured local secret values and common private-key/token signatures; no matches
were found. Local .env files remain ignored. This is a scoped check, not a guarantee
that every possible secret format is detectable.

The final diff against dev passes whitespace checks. The old staged merge diff
contains inherited EOF whitespace and a stale staged UserManagement version; avoid
editing teammate source solely to clean inherited formatting. Restage the final
reviewed content only at the authorized commit step.

Thenuri confirmed sidebar/pagination appearance and rejection email reason, as well
as earlier download, report, user-filter and notification checks. No further feature
work is identified in the agreed scope. Coordinate the authentication/merchant
behaviour with Risinu/Hasindu and the shared dispatch shell with Indu before merge.
