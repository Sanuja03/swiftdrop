import test from 'node:test'
import assert from 'node:assert/strict'
import { createHmac } from 'node:crypto'
import { once } from 'node:events'
import { createReceiver, verifySignature } from '../server.mjs'

const secret = 'whsec_mock_test_only_not_a_real_secret'
const seconds = 1794565800
const payload = id => JSON.stringify({ id, type: 'parcel.status_changed', createdAt: new Date(seconds * 1000).toISOString(),
  data: { trackingNumber: 'SDP-TEST-1', status: 'IN_TRANSIT', note: 'not shown in history' } })
const sign = (body, timestamp = seconds, key = secret) => 't=' + timestamp + ',v1=' + createHmac('sha256', key).update(timestamp + '.').update(body).digest('hex')
async function fixture(t, options = {}) {
  const server = createReceiver({ secret, now: () => seconds * 1000, ...options })
  server.listen(0, '127.0.0.1'); await once(server, 'listening')
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve) }))
  const base = 'http://127.0.0.1:' + server.address().port
  return { base, post: (body, signature = sign(body), headers = {}) => fetch(base + '/webhooks/swiftdrop', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-SwiftDrop-Signature': signature, ...headers }, body,
  }), history: async () => (await fetch(base + '/events')).json() }
}

test('matches the independently calculated known vector in Java WebhookSignerTest', () => {
  const body = '{"id":"1","type":"parcel.status_changed"}'
  const header = 't=1794565800,v1=79a791f6a18bface5bb8fefaf60ec2a4509f2c1fa4d49c128ebff6ea5ada1067'
  assert.equal(verifySignature('whsec_test_secret', header, Buffer.from(body), seconds), true)
  assert.equal(verifySignature('whsec_test_secret', header, Buffer.from(body + ' '), seconds), false)
})

test('rejects missing, malformed, duplicate and expired/future signature fields', () => {
  const body = payload('one'), signature = sign(body)
  for (const header of [undefined, '', 'v1=abc', signature + ',t=' + seconds, 't=0,v1=' + 'f'.repeat(64),
    sign(body, seconds - 301), sign(body, seconds + 301), signature.replace('t=1794565800', 't=NaN')]) {
    assert.equal(verifySignature(secret, header, Buffer.from(body), seconds), false)
  }
})

test('fails closed without a secret and rejects invalid demo limits', () => {
  assert.throws(() => createReceiver(), /SECRET/)
  assert.throws(() => createReceiver({ secret, failFirst: -1 }), /failFirst/)
  assert.throws(() => createReceiver({ secret, maxEvents: 0 }), /maxEvents/)
})

test('accepts the production envelope and exposes only safe history fields', async t => {
  const receiver = await fixture(t), body = payload('one')
  assert.equal((await receiver.post(body)).status, 200)
  const history = await receiver.history()
  assert.equal(history.events[0].id, 'one')
  assert.equal(history.events[0].trackingNumber, 'SDP-TEST-1')
  assert.equal(history.events[0].status, 'IN_TRANSIT')
  assert.equal(history.attempts[0].status, 200)
  const json = JSON.stringify(history)
  for (const forbidden of [secret, 'not shown in history', 'v1=', 'payload']) assert.equal(json.includes(forbidden), false)
  const page = await fetch(receiver.base + '/')
  assert.equal(page.status, 200)
  assert.match(page.headers.get('content-security-policy'), /frame-ancestors 'none'/)
})

test('accepts the actual flat test-ping contract separately from engine envelopes', async t => {
  const receiver = await fixture(t)
  const body = JSON.stringify({ eventId: 'ping-1', event: 'webhook.test_ping', merchantId: 'test-only', message: 'Test' })
  assert.equal((await receiver.post(body)).status, 200)
  const history = await receiver.history()
  assert.equal(history.events[0].type, 'webhook.test_ping')
  assert.equal(history.events[0].trackingNumber, null)
})

test('preserves raw UTF-8 bytes and rejects tampered/reformatted bodies or a wrong secret', async t => {
  const receiver = await fixture(t), body = payload('unicode').replace('not shown in history', 'ආයුබෝවන්')
  assert.equal((await receiver.post(body)).status, 200)
  assert.equal((await receiver.post(body + '\n', sign(body))).status, 401)
  assert.equal((await receiver.post(body, sign(body, seconds, 'wrong'))).status, 401)
  assert.equal((await receiver.history()).attempts.length, 1)
})

test('rejects invalid signatures before recording or parsing untrusted payloads', async t => {
  const receiver = await fixture(t)
  assert.equal((await receiver.post('{', 'invalid')).status, 401)
  assert.deepEqual(await receiver.history(), { events: [], attempts: [] })
})

test('returns 503 twice, then accepts the same event, and acknowledges duplicates once', async t => {
  const receiver = await fixture(t, { failFirst: 2 }), body = payload('retry-1')
  for (const expected of [503, 503, 200, 200]) assert.equal((await receiver.post(body)).status, expected)
  const history = await receiver.history()
  assert.equal(history.events.length, 1)
  assert.deepEqual(history.attempts.map(a => a.status), [200, 200, 503, 503])
  assert.equal(history.attempts[0].duplicate, true)
})

test('requires valid JSON and a matching stable event identity', async t => {
  const receiver = await fixture(t)
  assert.equal((await receiver.post('{')).status, 400)
  assert.equal((await receiver.post('{}')).status, 400)
  assert.equal((await receiver.post(payload('one'), undefined, { 'X-SwiftDrop-Event-Id': 'two' })).status, 400)
  assert.equal((await receiver.post(JSON.stringify({ id: 'one', eventId: 'two', type: 'event' }))).status, 400)
  assert.equal((await receiver.post(JSON.stringify({ id: 'one', type: 'event', data: null }))).status, 200)
})

test('bounds accepted events, attempts and duplicate bookkeeping in memory', async t => {
  const receiver = await fixture(t, { maxEvents: 2 })
  for (const id of ['one', 'two', 'three']) assert.equal((await receiver.post(payload(id))).status, 200)
  const history = await receiver.history()
  assert.deepEqual(history.events.map(e => e.id), ['three', 'two'])
  assert.equal(history.attempts.length, 2)
})

test('rejects oversized and unsupported content without storing it', async t => {
  const receiver = await fixture(t)
  assert.equal((await receiver.post('x'.repeat(256 * 1024 + 1))).status, 413)
  assert.equal((await receiver.post('{}', undefined, { 'Content-Type': 'text/plain' })).status, 415)
  assert.equal((await fetch(receiver.base + '/unknown')).status, 404)
  assert.deepEqual(await receiver.history(), { events: [], attempts: [] })
})
