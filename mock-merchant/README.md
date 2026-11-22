# Mock merchant webhook receiver

A dependency-free Node 22+ application with a small browser event-history page. It
implements the current `WebhookSigner` contract and both current payload shapes:
production `{id,type,createdAt,data}` events and `{eventId,event,...}` test pings.
It never generates/replaces a SwiftDrop signing secret or displays an existing one.

## Setup

Use a disposable, approved merchant. Obtain the new signing secret from the first
authenticated webhook configuration PUT or explicit rotation. Supply it privately
as `MOCK_MERCHANT_SECRET` in this process's environment; do not put it in source,
screenshots, shell-history examples or a committed environment file.

With Node already available, run:

```text
node mock-merchant/server.mjs
```

Open `http://127.0.0.1:9000`. The POST receiver is `/webhooks/swiftdrop`; `/events`
returns bounded, sanitized history and `/health` exposes no secret. The default
bind address is loopback. `MOCK_MERCHANT_PORT` may select another local port.
Restart the receiver after rotating its matching backend secret.

**SwiftDrop correctly rejects localhost/private callback URLs.** A normal backend
cannot be configured with this loopback URL. For a later authorized end-to-end demo,
use a public HTTPS development tunnel you control, forwarding only the receiver route,
and save that public URL through the normal merchant configuration endpoint. No tunnel
is installed/started here. Never disable the backend URL validator or send events to
someone else's endpoint. Automated backend tests can inject their isolated receiver
directory/transport without changing production SSRF policy.

## Verification and retry demonstration

The receiver validates `X-SwiftDrop-Signature: t=<seconds>,v1=<hex HMAC>` over the
exact bytes of `<t>.<raw body>`, with constant-time comparison and a five-minute
past/future timestamp tolerance. It rejects invalid signatures before logging data.
It acknowledges a duplicate accepted event with 200 without adding it again.

Optional `MOCK_MERCHANT_FAIL_FIRST=2` returns 503 for the first two verified attempts
of each new event, then 200. Send a normal parcel-status event through the existing
outbox/RabbitMQ engine to demonstrate real retries. Repeating the local POST in unit
tests demonstrates receiver responses only. Test pings are one-shot and do not receive
automatic retries; each new ping has a new event ID.

Only event ID/type, tracking number/status, reception time and attempt outcome are
retained. History/deduplication are in-memory and limited to 50 records; restarting or
eviction forgets older IDs. This is a demo receiver, not a durable production consumer.
The browser inserts event text with `textContent`, never untrusted HTML.

## Lightweight tests

```text
node --test mock-merchant/test/receiver.test.mjs
```

These tests use short-lived loopback receivers and no packages, database, Docker,
Spring Boot or external URLs. The known HMAC vector matches the Java signing test.
