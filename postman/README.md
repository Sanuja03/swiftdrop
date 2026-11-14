# SwiftDrop API tests

Import the collection and environment JSON files here and select **SwiftDrop Local**.
Use a current Postman Collection Runner or Postman CLI supporting
[`pm.execution.skipRequest()`](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-execution).
Older runtimes cannot reliably apply the prerequisite guards.

## Setup

Use a dedicated local test database. `baseUrl` defaults to `http://localhost:8080`;
specify an origin without `/api`, another path, or a trailing slash. Apply
`backend/src/main/resources/schema.sql` and the courier and merchant webhook setup
scripts in `backend/src/main/resources/db/scripts/`. Seed active zones/pricing rules.
The collection discovers their IDs and service types; no user IDs are hard-coded.

Set credentials locally; committed credential/token fields are blank:

| Variables | Test account/fixture |
|---|---|
| `customerEmail`, `customerPassword` | Verified, ACTIVE LOCAL `ROLE_CUSTOMER` |
| `merchantEmail`, `merchantPassword` | Verified, approved ACTIVE LOCAL `ROLE_MERCHANT` |
| `otherCustomerEmail`, `otherCustomerPassword` | Optional distinct customer for booking ownership |
| `otherMerchantEmail`, `otherMerchantPassword` | Optional distinct, configured merchant for log isolation |
| `operatorEmail`, `operatorPassword` | Optional verified, ACTIVE `ROLE_ADMIN` or `ROLE_DISPATCHER` |
| `webhookUrl` | Optional public ASCII HTTP(S) receiver you control |
| `seededTrackingNumber` | Optional parcel when creation is disabled; own-booking tests require ownership by the first customer |

Provision accounts through local seeds or normal signup, email verification and
merchant approval. No provisioning endpoints or Google mock-token shortcut are
invented. Runtime `accessToken`, refresh/role tokens, tracking numbers and delivery
IDs are captured from responses. Passwords/tokens/codes are marked secret. Keep
populated environments/history private; clear secrets before exporting or committing.
Scripts never save/log webhook signing secrets.

## Run in order

Choose **Run collection**, select the environment, and use **one iteration**:

1. **00** resets derived values and discovers public zones/pricing.
2. **01** tests auth validation, customer login, refresh and profile access.
3. **02-03** create/read a booking when enabled, test ownership and public tracking privacy.
4. **04** logs in the merchant and tests configuration, access restrictions and unsafe URLs.
5. **05-06** test merchant/operator delivery lists, details, ownership, paging and optional retries.
6. **07** logs out captured sessions and clears their local tokens.
7. **08** holds opt-in interactive account/email/Google flows.

With Postman CLI already installed, from the repository root:

```text
postman collection run postman/SwiftDrop_API_Tests.postman_collection.json -e postman/SwiftDrop_Local.postman_environment.json
```

For unattended runs, use a protected local environment copy with test credentials;
do not commit it or put passwords in shell commands. No CLI was installed here.
Manual requests need their discovery/login prerequisites; folder 00 clears tokens.
Missing prerequisites/disabled flags produce a console `SKIP:` reason.
**Skipped requests run no assertions and are not passes.** Review skips with failures.

## Writes and infrastructure

- `allowWrites=false` by default. Enable it for positive booking/configuration
  requests. Login/refresh/logout still manage session records normally. Each enabled
  booking run creates a parcel; no delete endpoint or cleanup SQL is invented.
- Configuration PUT changes the test merchant's URL. A secret is generated only if
  missing and returned once; save it privately from that response if needed.
  Repeated PUT and GET must omit secrets. No rotation/recovery endpoint exists.
- Configure or seed the merchant first. Signup creates no merchant row, so log
  cases skip when configuration reports `configured=false`. The second merchant
  also needs its own configuration for isolation checks.
- Empty logs are valid. Detail/retry/isolation cases capture IDs from lists and skip
  without fixtures. Generate logs using a trusted seeded merchant-linked parcel
  and the existing status workflow. Customer bookings remain unlinked.
- Delivery requires RabbitMQ, the outbox publisher/consumer and a public receiver;
  positive configuration needs DNS. Read/validation requests need the database.
- Actual retries additionally require `allowWebhookRetry=true` and can send real
  signed webhooks. Seed DEAD and stable non-DEAD deliveries. Concurrent workers can
  invalidate 409 fixtures. Manual retries accept DELIVERED or DEAD, as implemented.
- Logout revokes refresh tokens, not issued JWTs; immediate JWT revocation is not asserted.

## Optional interactive flows

Folder 08 flags default to false. With `allowWrites=true`, `runRegistration=true`
uses fresh `registrationEmail`/`registrationPassword`, then tests duplicate signup.
Use a reachable mailbox. Signup returns null tokens pending verification.

Enable `runEmailFlows=true` for manual inbox steps: resend while unverified, supply
`verificationCode`, then verify. Forgot-password emails the LOCAL customer; its
response exposes no code. Supply `resetCode`/`newPassword` and separately enable
`runPasswordReset=true` to change its password; update local credentials afterwards.
SMTP is required; this is not a single unattended email workflow.

Google authentication needs a real test `googleIdToken`, `runGoogleAuth=true`,
`allowWrites=true` and access to Google. It may create a user. Default execution
covers required-field validation for every auth endpoint without identity/email calls.

## Validation and limitations

Contracts were checked against controllers, DTOs, security and tests in the auth,
booking, pricing, tracking and webhook modules. Auth uses `error`/`validationErrors`;
unknown refresh tokens return **401**. Other covered errors use problem details.
Anonymous `/auth/me` returns an empty 401. Log pages use
`content/page/size/totalElements/totalPages`, with clamped pagination.

Both JSON files passed parsing/structural checks; 141 embedded scripts passed syntax
checks, and pre-request guards/body substitution were exercised with mocked inputs.
This is not full Postman-runtime or official JSON Schema validation. No suitable
local listener was found on port 8080, so **no live API requests/Postman assertions
were executed against SwiftDrop**. Email, Google, real webhook delivery/retries and
receiver signature verification remain unexecuted.
