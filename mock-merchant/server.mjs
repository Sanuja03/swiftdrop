import { createHmac, timingSafeEqual } from 'node:crypto'
import { createServer } from 'node:http'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const maxBodyBytes = 256 * 1024
const assets = new Map([
  ['/', ['text/html; charset=utf-8', readFileSync(new URL('./public/index.html', import.meta.url))]],
  ['/app.js', ['text/javascript; charset=utf-8', readFileSync(new URL('./public/app.js', import.meta.url))]],
])

// Match WebhookSigner: t=<seconds>,v1=HMAC-SHA256(secret, timestamp + "." + raw UTF-8 body).
export function verifySignature(secret, header, rawBody, nowSeconds, toleranceSeconds = 300) {
  if (typeof secret !== 'string' || !secret || typeof header !== 'string') return false
  const fields = header.split(',').map(part => part.trim().split('='))
  if (fields.length !== 2 || fields.some(pair => pair.length !== 2)) return false
  const values = Object.fromEntries(fields)
  if (!/^\d+$/.test(values.t ?? '') || !/^[a-f0-9]{64}$/i.test(values.v1 ?? '')) return false
  const timestamp = Number(values.t)
  if (!Number.isSafeInteger(timestamp) || Math.abs(nowSeconds - timestamp) > toleranceSeconds) return false
  const expected = createHmac('sha256', secret).update(values.t + '.').update(rawBody).digest()
  return timingSafeEqual(expected, Buffer.from(values.v1, 'hex'))
}

export function createReceiver({ secret, failFirst = 0, maxEvents = 50, now = () => Date.now() } = {}) {
  if (typeof secret !== 'string' || !secret) throw new Error('Set MOCK_MERCHANT_SECRET to the merchant signing secret')
  if (!Number.isInteger(failFirst) || failFirst < 0 || failFirst > 100) throw new Error('failFirst must be between 0 and 100')
  if (!Number.isInteger(maxEvents) || maxEvents < 1 || maxEvents > 200) throw new Error('maxEvents must be between 1 and 200')
  const events = new Map()
  const attempts = []
  const attemptCounts = new Map()
  const send = (response, status, data) => {
    response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
    response.end(JSON.stringify(data))
  }

  return createServer(async (request, response) => {
    response.setHeader('Cache-Control', 'no-store')
    response.setHeader('X-Content-Type-Options', 'nosniff')
    response.setHeader('Content-Security-Policy', "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'")
    if (request.method === 'GET' && assets.has(request.url)) {
      const [type, body] = assets.get(request.url)
      response.writeHead(200, { 'Content-Type': type }); response.end(body); return
    }
    if (request.method === 'GET' && request.url === '/health') {
      send(response, 200, { status: 'ok', failFirst, maxEvents }); return
    }
    if (request.method === 'GET' && request.url === '/events') {
      // Never include the signing secret, raw signature, full payload, addresses or phone numbers.
      send(response, 200, { events: [...events.values()].reverse(), attempts: [...attempts].reverse() }); return
    }
    if (request.method !== 'POST' || request.url !== '/webhooks/swiftdrop') {
      send(response, 404, { error: 'Not found' }); return
    }
    if (!/^application\/json(?:;|$)/i.test(request.headers['content-type'] ?? '')) {
      send(response, 415, { error: 'JSON required' }); request.resume(); return
    }
    let bytes = 0
    const chunks = []
    let tooLarge = false
    request.on('data', chunk => {
      bytes += chunk.length
      if (bytes > maxBodyBytes) {
        if (!tooLarge) send(response, 413, { error: 'Payload too large' })
        tooLarge = true; chunks.length = 0
      } else if (!tooLarge) chunks.push(chunk)
    })
    request.on('error', () => { if (!response.headersSent) send(response, 400, { error: 'Invalid request' }) })
    request.on('end', () => {
      if (tooLarge) return
      const body = Buffer.concat(chunks)
      if (!verifySignature(secret, request.headers['x-swiftdrop-signature'], body, Math.floor(now() / 1000))) {
        send(response, 401, { error: 'Invalid or expired signature' }); return
      }
      let payload
      try { payload = JSON.parse(body.toString('utf8')) } catch {
        send(response, 400, { error: 'Invalid JSON' }); return
      }
      // Production events use id/type/data; the existing test-ping route uses eventId/event.
      const id = payload?.id ?? payload?.eventId
      const type = payload?.type ?? payload?.event
      if (typeof id !== 'string' || !id || id.length > 100 || typeof type !== 'string' || !type || type.length > 100
          || (payload.id && payload.eventId && payload.id !== payload.eventId)
          || (request.headers['x-swiftdrop-event-id'] && request.headers['x-swiftdrop-event-id'] !== id)) {
        send(response, 400, { error: 'Invalid event identity' }); return
      }
      const duplicate = events.has(id)
      const attempt = (attemptCounts.get(id) ?? 0) + 1
      attemptCounts.set(id, attempt)
      while (attemptCounts.size > maxEvents) attemptCounts.delete(attemptCounts.keys().next().value)
      const status = !duplicate && attempt <= failFirst ? 503 : 200
      const record = { id, type, attempt, status, duplicate, receivedAt: new Date(now()).toISOString() }
      attempts.push(record)
      if (attempts.length > maxEvents) attempts.shift()
      if (status === 200 && !duplicate) {
        const data = payload.data && typeof payload.data === 'object' ? payload.data : payload
        events.set(id, { id, type, trackingNumber: typeof data.trackingNumber === 'string' ? data.trackingNumber.slice(0, 100) : null,
          status: typeof data.status === 'string' ? data.status.slice(0, 50) : null, receivedAt: record.receivedAt })
        if (events.size > maxEvents) events.delete(events.keys().next().value)
      }
      send(response, status, status === 503 ? { accepted: false, retry: true } : { accepted: true, duplicate })
    })
  })
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const port = Number(process.env.MOCK_MERCHANT_PORT ?? 9000)
    if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Port must be between 1024 and 65535')
    const server = createReceiver({ secret: process.env.MOCK_MERCHANT_SECRET, failFirst: Number(process.env.MOCK_MERCHANT_FAIL_FIRST ?? 0) })
    server.on('error', () => { console.error('Mock merchant could not start; check the local port'); process.exitCode = 1 })
    server.listen(port, '127.0.0.1', () => console.log(`Mock merchant: http://127.0.0.1:${port} (secret is never displayed)`))
  } catch (error) { console.error(error.message); process.exitCode = 1 }
}
