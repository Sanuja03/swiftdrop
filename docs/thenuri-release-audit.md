# Notifications and reports release audit

Base: `origin/dev` at `7e2764e` (merged audit PR #10).

## Scope

This branch verifies Thenuri's two required release checks. It adds tests and this
record only. It does not change production code, database tables or columns,
Compose, Dockerfiles, Kubernetes/Helm resources, ports, dependencies or secrets.
Earlier uncommitted merchant/account-status and operational-notification work is
preserved separately on `feature/admin-reports-notifications` and is not included.

## Schema alignment

The consolidated `schema.sql` definitions for `notifications` and
`report_snapshots` match the original module SQL scripts. Notification entity
column names match the audit table, and the JDBC snapshot repository uses the
six columns defined in the audit. The H2 dev snapshot definition has the same
column types, lengths and nullability.

`AuditSchemaContractTest` extracts these table definitions from the actual SQL
files. It verifies column parity, default unread/type/timestamp values, duplicate
notification event rejection, notification user/parcel deletion behaviour,
snapshot JSON persistence and listing, snapshot author deletion behaviour, and
invalid date-range rejection.

The isolated H2 test adapts PostgreSQL's `TIMESTAMPTZ` alias, UUID default function
and default/primary-key ordering. It does not validate PostgreSQL RLS, partial
indexes or the live Supabase schema. Sanuja's schema application and clean
PostgreSQL release check remain necessary. No shared database was modified here.

## Reports against the real demo seeder

`DemoReportAuditTest` invokes the actual `DemoDataSeeder` with real test
repositories, then runs `ReportService` queries. All records are synthetic and
rolled back; mail is mocked, RabbitMQ listeners are stopped, and outbox publishing
is disabled.

| Scenario | Expected result |
| --- | --- |
| Unchanged demo data | 3 parcels; 1 created, 1 in transit, 1 out for delivery |
| Demo fees | 590 + 1490 + 890 = 2970.00 |
| Initial outcomes | 0 delivered, 0 failed; both rates 0%; no daily deliveries |
| Run seeder twice | Counts and fees unchanged |
| Deliver express parcel with proof; fail in-transit parcel after dispatch | 1 delivered, 1 failed; success/failure each 50%; fees unchanged |
| Outcome date, fixed to 21 November in Colombo | 1 daily delivery; none on 22 November |

The existing `ReportQueryTest` also covers Colombo midnight boundaries, inclusive
date ranges, repeated history rows and latest-outcome aggregation.

Run from `backend`:

```sh
./mvnw -B -Duser.timezone=UTC -Dtest=AuditSchemaContractTest,DemoReportAuditTest,ReportQueryTest,ReportSnapshotServiceTest,NotificationServiceTest test
```

Local result on 21 November 2026: **24 tests passed, zero failures, errors or
skips**, with the JVM timezone set to UTC. No production-code or schema fix was
needed for these checks.

## Earlier feature work and team integration

Keep optional features out of this verification PR. The earlier operational
alerts reuse RabbitMQ and existing notification storage, but their behaviour must
be checked with Indu's assignment-completion change: resolving a failed parcel's
responsible dispatcher by *active* assignment can miss an assignment already
marked completed. Account-status changes also require the deployed backend
version to recognise `REJECTED`.

These are integration considerations, not changes to Kubernetes in this branch.
Any later feature PR needs the leader's scope agreement and a check against the
then-current dev branch. CI must be green before the team merges this PR.

### Decision on combining earlier changes

Reviewed against freshly fetched `origin/dev` (`7e2764e`): do not combine the
earlier feature bundle with this schema/report verification PR.

| Earlier change | Assessment for this PR |
| --- | --- |
| Hide merchants without business applications from All Users; refresh both lists after a decision | Lower deployment risk, but changes user-list visibility and API availability dependencies. Extract into a separate focused PR if agreed. No Kubernetes configuration needed. |
| Reapprove rejected merchants and rotate their revoked API keys | Changes business decisions and key lifecycle. Keep in a separate merchant-account PR with the status and verification tests. |
| Store `REJECTED` in `users.status` | Latest dev's enum does not recognise it. All relevant backend versions must understand the value; an old version can fail to load those accounts. The earlier SQL correction must not be applied as part of this verification PR. |
| Block generic suspend/restore for all merchants | Needs a resolution for already-suspended merchants: approval refuses `SUSPENDED`, and generic restore is now blocked too, leaving no supported admin recovery route. Do not include unchanged. |
| Courier/dispatcher alerts, bells and courier layout refactor | Additional runtime queues and integration with assignment completion. Defer; the completed-assignment lookup problem remains. |
| Merchant parcel outcome and account-access emails | New messaging/email behaviour, not required schema verification. Keep separate; real SMTP/broker delivery has not been verified for these additions. |

The audit also changed `AuthService` to reject self-service staff registration,
disable mock Google tokens by default and stop logging verification/reset codes.
Do not copy the older local `AuthService` over latest dev; future integration must
preserve those security fixes. Passing tests on the old feature branch is not
evidence that the combined release version has been tested.

The verification-only changes here do not require any Kubernetes/Helm changes.
No optional production change has been transferred into this branch.
