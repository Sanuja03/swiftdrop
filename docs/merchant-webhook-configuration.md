# Merchant webhook configuration

The configuration API uses the existing signing, outbox, RabbitMQ delivery, retry,
dead-letter and delivery-log engine. It does not introduce a second sender.

## Apply the schema before deploying

Apply `backend/src/main/resources/db/scripts/merchant_webhook_setup.sql` manually
to the application PostgreSQL database **after** the shared `schema.sql`. The
application still uses `ddl-auto=none`; this change does not connect to or migrate
your database automatically. Courier schema setup remains separate.

The migration creates `merchants` if missing, adds nullable webhook columns to an
existing merchant table, enforces one merchant per `user_id`, and adds nullable
`parcels.merchant_id`. Existing users, merchant IDs, URLs, secrets and parcels are
preserved. Existing customer parcels remain NULL and produce no merchant webhook.
The script is repeatable and transactional. If an existing merchant table has
duplicate `user_id` values, the unique index fails and rolls back: review the
ownership conflict rather than deleting or reassigning records. Existing tables
must follow the UUID `merchants.id` / `merchants.user_id` contract. Coordinate any
future merchant module's additional required columns before merging its model.

Deploying the new parcel model before this migration will make parcel queries fail
because `merchant_id` is missing. Apply the migration first; it remains compatible
with the old application. If `merchant_id` already exists, the script preserves it
and does not retrofit/validate a foreign key on that existing column.

## API

Both endpoints require `Authorization: Bearer <access token>` for an ACTIVE merchant
user. Ownership comes from the authenticated email resolved to `users.id`, never
from request/query IDs. No changes to auth registration or customer booking APIs
are required. A merchant row is created lazily on the first successful PUT.

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/v1/merchant/webhooks/config` | Returns configuration without a secret; does not create rows |
| PUT | `/api/v1/merchant/webhooks/config` | Creates or updates the caller's URL; generates a secret only if none exists |

PUT request:

```json
{"url":"https://your-public-domain.com/swiftdrop/events"}
```

First successful PUT response (save the secret securely when received):

```json
{"url":"https://your-public-domain.com/swiftdrop/events","configured":true,"secret":"whsec_<random-value>"}
```

The secret contains 32 cryptographically random bytes encoded as URL-safe Base64.
GET and subsequent PUT responses omit `secret` entirely. GET before configuration
returns `{"configured":false}`. Responses use `Cache-Control: no-store`.
URL updates preserve the secret; existing secrets are not recoverable through this
API. Secret rotation, disabling, and UI controls are outside this change.

Invalid destinations/body fields return 400, missing/invalid authentication returns
401, and other roles, deleted users or inactive merchants return 403.

## Destinations and local testing

Only absolute HTTP(S) URLs up to 500 ASCII characters are accepted. Credentials,
fragments, invalid ports, scoped IPv6 addresses, localhost/internal names,
unresolvable hosts, and any DNS answer in a private, loopback, link-local,
multicast or reserved address range are rejected. Mixed public/private DNS answers
are also rejected. The directory revalidates current DNS when resolving an endpoint
for the existing sender, including retries and legacy configurations.

There is **no local-address bypass**, including under the dev profile. For manual
testing use a public HTTPS receiver you control. Existing engine tests can still
inject a fake merchant directory and use their isolated loopback HTTP receiver;
that does not expose a production configuration bypass.

DNS validation and the JDK HTTP client's actual DNS lookup are separate operations;
revalidation does not fully prevent DNS rebinding between them. Production deployments
should enforce outbound network restrictions to internal/metadata destinations.
Redirects remain disabled in the existing client. DNS resolution itself uses the
system resolver; the existing HTTP request timeout does not bound DNS validation.

## Parcel association

The directory uses the same columns the API persists:
`users.id -> merchants.user_id`, then `merchants.id -> parcels.merchant_id`.
Trusted merchant booking/import code can persist the nullable `Parcel.merchantId`
using the merchant identity resolved on the server. This change does not grant
merchants access to the CUSTOMER-only booking API or invent an assignment API.
Existing parcels are never automatically converted from customer to merchant
ownership. For an integration demo, seed a parcel linked to the configured merchant
through trusted test/setup code, then change its status through the normal service.

The unchanged engine resolves the endpoint, signs with its stored secret, sends the
event, and records its result. Existing merchant/admin delivery-log and retry APIs
remain available. See `messaging-and-webhooks.md` for headers and signature verification.

## Verification

From `backend`, using Java 21 and Maven:

```text
mvn -B "-Dtest=WebhookConfigurationIntegrationTest,MerchantWebhookMigrationTest,WebhookUrlValidatorTest,JdbcMerchantWebhookDirectoryTest,WebhookHttpClientTest,WebhookSignerTest,WebhookDispatchServiceTest,WebhookDeliveryServiceTest,WebhookDeliveryControllerTest,RetryPolicyTest" test
```

The configuration integration test uses real JWTs, security, HTTP controllers,
H2 persistence and SQL directory resolution. DNS answers and the outbound HTTP
transport are mocked; the existing engine/signing code runs and the signature is
verified using the saved configuration. Migration tests run the actual additive
script twice against H2 in PostgreSQL mode and check data preservation and links;
they do not replace PostgreSQL deployment validation. Existing real RabbitMQ tests
remain in `EventFlowIntegrationTest` and require Docker.
