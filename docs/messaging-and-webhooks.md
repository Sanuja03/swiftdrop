# Messaging & Webhook Engine

Owner: Sanjalee (WBS 2.2). Code: `backend/.../modules/messaging` and `backend/.../modules/webhook`.

## What it does

Every parcel status change is published to RabbitMQ and delivered to the parcel's merchant
as a signed webhook, with retries and a dead-letter queue.

```
ParcelStatusService.updateStatus()
  └─ ParcelStatusChangedEvent ──(same DB transaction)──▶ OutboxEventRecorder
                                                          └─ OutboxService.publishEvent() → outbox_events (PENDING)
OutboxPublisher (every 2s, FOR UPDATE SKIP LOCKED, publisher confirms)
  └─ exchange "swiftdrop.events" (topic), routing key parcel.status.<status>
       ├─▶ "swiftdrop.webhooks.events" ─▶ WebhookConsumer ─▶ WebhookDispatchService
       │        signed POST to merchant ──┬─ 2xx  → DELIVERED
       │                                  ├─ fail → retry queue (TTL 10s, 30s, 2m, 10m, 30m) → attempts queue → try again
       │                                  └─ out of attempts → DEAD, message → "swiftdrop.webhooks.dlq"
       └─▶ (your queue here — e.g. notifications)
```

**Patterns used**: transactional outbox, publish/subscribe (topic exchange), message broker,
retry with delay queues (TTL + dead-lettering), dead-letter channel, idempotent consumer
(unique `event_id + merchant_id`), Observer (Spring events), configurable retry policy.

**Why an outbox?** Writing to RabbitMQ directly inside the status update could lose the event
(broker down, crash after commit) or publish an event for a change that was rolled back. The
outbox row commits with the status change; the relay keeps trying until RabbitMQ confirms it.
Delivery is at-least-once, so consumers must ignore an `eventId` they have already handled.

## Setup

1. Apply the "Messaging & Webhook Engine" section at the end of `backend/src/main/resources/schema.sql`
   in the Supabase SQL editor (creates `outbox_events` and `webhook_deliveries`).
   **Do this before running this branch**: status updates write an outbox row, so they fail if the table is missing.
2. RabbitMQ on `localhost:5672` with `guest/guest` works with no config. Otherwise set
   `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`.
3. Exchanges and queues are created automatically on startup. Watch them at http://localhost:15672.

Optional: `WEBHOOK_RETRY_DELAYS` (default `10s,30s,2m,10m,30m`), `OUTBOX_POLL_INTERVAL_MS` (default `2000`).
For a demo, `WEBHOOK_RETRY_DELAYS=5s,10s` makes retries visible quickly.

## For other modules

### Publishing your own event (anyone)

```java
outboxService.publishEvent("merchant", merchantId, "merchant.approved", "merchant.approved", payload);
```
Must be called inside a `@Transactional` method (it refuses otherwise). Routing key = how consumers bind.

### Consuming status events (Thenuri — notifications)

Declare your own durable queue bound to `swiftdrop.events` with `parcel.#` (or `parcel.status.delivered`
etc.) and listen with a `DomainEventMessage` parameter:

```java
@Bean Queue notificationQueue() { return QueueBuilder.durable("swiftdrop.notifications").build(); }
@Bean Binding notificationBinding(Queue notificationQueue, TopicExchange eventsExchange) {
    return BindingBuilder.bind(notificationQueue).to(eventsExchange).with("parcel.#");
}

@RabbitListener(queues = "swiftdrop.notifications")
void on(DomainEventMessage event) {
    var p = jsonMapper.treeToValue(event.payload(), ParcelStatusChangedPayload.class);
    // p.parcelId(), p.trackingNumber(), p.previousStatus(), p.status(), p.note(), p.changedAt()
}
```
The payload has no addresses or phone numbers; load contact details by `parcelId`.

### Merchant data (Hasindu, Janani)

The engine reads merchants through `MerchantWebhookDirectory`. The current
`JdbcMerchantWebhookDirectory` uses `parcels.merchant_id`, `merchants.user_id`,
`merchants.webhook_url` and `merchants.webhook_secret` (from `feature/merchant-api`), and simply
sends nothing if those don't exist yet.

- **Janani**: when `webhook_endpoints` / `getWebhookEndpoint()` land, change `findEndpoint()` to use them.
  Your delivery-log page can use the API below.
- **Hasindu**: `MerchantWebhookService` on your branch sends webhooks directly. Once merged, webhook
  sending should go through this engine (status changes already do, automatically), so merchants
  don't get every update twice.

## Delivery-attempts API

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/merchant/webhooks/deliveries?status=&page=&size=` | MERCHANT (own only) |
| GET | `/api/v1/merchant/webhooks/deliveries/{id}` | MERCHANT (own only; others' → 404) |
| POST | `/api/v1/merchant/webhooks/deliveries/{id}/retry` | MERCHANT, DEAD deliveries only |
| GET | `/api/v1/admin/webhooks/deliveries?merchantId=&status=&page=&size=` | ADMIN, DISPATCHER |
| GET | `/api/v1/admin/webhooks/deliveries/{id}` | ADMIN, DISPATCHER |
| POST | `/api/v1/admin/webhooks/deliveries/{id}/retry` | ADMIN, DISPATCHER, DEAD deliveries only |

`status` is one of `PENDING`, `RETRYING`, `DELIVERED`, `DEAD`. Newest first, max 100 per page.
A manual retry makes one immediate attempt; if it fails the delivery is `DEAD` again.

## What a merchant receives

```
POST <merchant webhook URL>
Content-Type: application/json
X-SwiftDrop-Event: parcel.status_changed
X-SwiftDrop-Event-Id: 4f0c1c9e-...        (same on every retry — use it to ignore duplicates)
X-SwiftDrop-Delivery: 9a7e...
X-SwiftDrop-Attempt: 1
X-SwiftDrop-Signature: t=1794565800,v1=79a791f6...

{"id":"4f0c1c9e-...","type":"parcel.status_changed","createdAt":"2026-11-13T09:30:00Z",
 "data":{"trackingNumber":"SDP7845621","status":"IN_TRANSIT","statusLabel":"In Transit",
         "previousStatus":"PICKED_UP","note":null,"changedAt":"2026-11-13T09:30:00Z"}}
```

**Verifying the signature** (merchant side): compute `HMAC-SHA256(secret, t + "." + rawBody)` as hex,
compare it with `v1` in constant time, and reject if `t` is more than 5 minutes old.
Any 2xx answer counts as delivered; redirects are not followed.

## Security notes

- Secrets are never logged, returned by the API, or stored in `webhook_deliveries`.
- Merchant response bodies are discarded; only the status code is stored.
- Only `http`/`https` URLs are called; 3 s connect and 5 s total timeouts.
- Webhook bodies contain no recipient addresses or phone numbers.

## Tests

| Test | Covers |
|---|---|
| `EventFlowIntegrationTest` | Real RabbitMQ (Testcontainers): status update → outbox → broker → signed webhook; retries through TTL queues; DLQ; rollback leaves no event. Skipped without Docker. |
| `WebhookDispatchServiceTest` | Webhook retry test: delays in order, success stops retries, exhaustion → DEAD, manual retry, skips, duplicates. |
| `OutboxPublisherTest`, `OutboxServiceTest`, `OutboxEventRecorderTest` | Relay, confirms, backoff, payload mapping. |
| `WebhookDeliveryControllerTest` | Role rules on the real `SecurityConfig`. |
| `WebhookDeliveryServiceTest` | Merchant isolation. |
| `WebhookSignerTest`, `WebhookHttpClientTest`, `JdbcMerchantWebhookDirectoryTest`, `RetryPolicyTest` | Signing (checked against openssl), HTTP behaviour, SQL lookups. |

Run only these: `./mvnw test -Dtest='Outbox*Test,Webhook*Test,RetryPolicyTest,EventFlowIntegrationTest,JdbcMerchant*Test'`
