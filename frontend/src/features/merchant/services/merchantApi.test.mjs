import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'

// Execute the actual service with its HTTP import stubbed; no Vite or network needed.
const source = readFileSync(new URL('./merchantApi.js', import.meta.url), 'utf8')
function service(response) {
  const calls = []
  const context = { URLSearchParams, request: async (...args) => {
    calls.push(JSON.parse(JSON.stringify(args)))
    if (response instanceof Error) throw response
    return response
  } }
  runInNewContext(source.replace(/^import .*httpClient'\r?\n/m, '')
    .replace('export const merchantApi =', 'globalThis.merchantApi ='), context)
  return { api: context.merchantApi, calls }
}

test('configuration uses the shared contract and never sends caller IDs or secrets', async () => {
  const { api, calls } = service({ configured: true })
  await api.getWebhookConfig()
  await api.updateWebhook('untrusted-id', { url: 'https://hooks.shop.com/events', secret: 'never-send' })
  assert.deepEqual(calls, [
    ['/merchant/webhooks/config'],
    ['/merchant/webhooks/config', { method: 'PUT', json: { url: 'https://hooks.shop.com/events' } }],
  ])
})

test('secret recovery is an explicit POST without a caller-supplied identity', async () => {
  const response = { url: 'https://hooks.shop.com/events', configured: true, secret: 'test-only-new-secret' }
  const { api, calls } = service(response)
  assert.equal(await api.rotateWebhookSecret(), response)
  assert.deepEqual(calls, [['/merchant/webhooks/config/rotate-secret', { method: 'POST' }]])
})

test('history reads the actual engine page and preserves its status fields', async () => {
  const content = [{ id: 'test-delivery', status: 'DELIVERED', lastResponseCode: 204, lastError: null }]
  const { api, calls } = service({ content })
  assert.equal(await api.getWebhookLogs('ignored-id'), content)
  assert.deepEqual(calls, [['/merchant/webhooks/deliveries?page=0&size=50']])
})

test('authorization errors propagate without fake configuration or history', async () => {
  const error = new Error('Forbidden')
  const { api } = service(error)
  await assert.rejects(api.getWebhookConfig(), error)
  await assert.rejects(api.getWebhookLogs(), error)
})

test('test delivery sends only the destination override to the authenticated compatibility route', async () => {
  const response = { success: true, statusCode: 204, latencyMs: 3, message: 'Ping succeeded (HTTP 204)' }
  const { api, calls } = service(response)
  assert.equal(await api.testWebhook('ignored-id', 'https://hooks.shop.com/test'), response)
  // The shared request client defaults JSON-body requests to POST.
  assert.deepEqual(calls, [['/merchant/webhook/test', { json: { webhookUrl: 'https://hooks.shop.com/test' } }]])
})

test('validation errors on save, rotation and test delivery propagate unchanged', async () => {
  const error = new Error('Invalid webhook destination')
  const { api } = service(error)
  await assert.rejects(api.updateWebhook('ignored-id', { url: 'http://localhost' }), error)
  await assert.rejects(api.rotateWebhookSecret(), error)
  await assert.rejects(api.testWebhook('ignored-id', 'http://localhost'), error)
})
