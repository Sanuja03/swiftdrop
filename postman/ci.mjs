// CI-only export of existing, credential-free requests. No backend/service is started here.
import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const sourceCollectionPath = new URL('./SwiftDrop_API_Tests.postman_collection.json', import.meta.url)
export const selectedNames = [
  'List public service prices',
  'Login rejects missing credentials',
  'signup rejects missing required fields',
  'google rejects missing required fields',
  'verify-email rejects missing required fields',
  'resend-verification rejects missing required fields',
  'forgot-password rejects missing required fields',
  'reset-password rejects missing required fields',
  'GET /api/v1/merchant/webhooks/config rejects anonymous caller',
  'PUT /api/v1/merchant/webhooks/config rejects anonymous caller',
  'Singular secret rotation rejects anonymous',
  'Plural secret rotation rejects anonymous',
  'Anonymous GET delivery access is 401',
  'Anonymous POST delivery access is 401',
  'API-key mutation /api-key/rotate rejects anonymous',
  'API-key mutation /api-key/regenerate rejects anonymous',
  'Admin approve rejects anonymous',
  'Admin reject rejects anonymous',
  'Machine API rejects a missing API key',
  'Anonymous logout is idempotent',
]

function flatten(items) {
  return items.flatMap(item => item.item ? flatten(item.item) : [item])
}

export function buildCICollection(source = JSON.parse(readFileSync(sourceCollectionPath, 'utf8'))) {
  const requests = new Map(flatten(source.item).map(item => [item.name, item]))
  const items = selectedNames.map(name => {
    assert.ok(requests.has(name), `CI source request no longer exists: ${name}`)
    const item = structuredClone(requests.get(name))
    assert.equal(item.request.auth?.type, 'noauth', `CI smoke must not use stored credentials: ${name}`)
    assert.ok(!item.request.header.some(h => /^(authorization|x-api-key)$/i.test(h.key)), `Credential header in ${name}`)
    // Literal loopback target: environment overrides cannot redirect CI requests elsewhere.
    assert.ok(item.request.url.startsWith('{{baseUrl}}/api/v1/'))
    item.request.url = item.request.url.replace('{{baseUrl}}', 'http://127.0.0.1:8080')
    for (const event of item.event ?? []) {
      if (event.listen === 'prerequest') {
        // These selected anonymous/invalid-body cases cannot perform successful business writes.
        // Their normal collection-wide write guard is replaced by the explicit isolated-CI guard.
        event.script.exec = event.script.exec.filter(line => !(line.includes('get("allowWrites")') && line.includes('skipRequest')))
      }
      assert.ok(!/pm\.execution\.(skipRequest|runRequest)|pm\.sendRequest/.test(event.script.exec.join('\n')),
        `Unexpected optional workflow or nested request in ${name}`)
    }
    return item
  })
  return {
    info: { name: 'SwiftDrop Newman CI smoke', schema: source.info.schema,
      description: '20 existing public/authentication/validation cases. Isolated H2 CI backend only; no accounts, callbacks, parcel creation or full lifecycle execution.' },
    variable: [{ key: 'ciIsolatedDatabase', value: 'false' }],
    event: [
      { listen: 'prerequest', script: { type: 'text/javascript', exec: [
        'if (pm.variables.get("ciIsolatedDatabase") !== "true") throw new Error("Use only the isolated CI H2 backend");',
        'pm.variables.set("__responseKind", "json");',
        'pm.variables.set("randomId", pm.variables.replaceIn("{{$guid}}"));',
      ] } },
      ...structuredClone(source.event.filter(event => event.listen === 'test')),
    ],
    item: items,
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const output = process.argv[2]
  if (!output) throw new Error('Usage: node postman/ci.mjs <new-generated-output.json>')
  const collection = buildCICollection()
  writeFileSync(output, JSON.stringify(collection, null, 2) + '\n', { flag: 'wx' })
  console.log(`Prepared ${collection.item.length} existing requests for Newman; no HTTP calls made`)
}
