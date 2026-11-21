// Offline checks only: no packages, HTTP requests, services or database access.
import assert from 'node:assert/strict'
import { readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { resolve, dirname, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { Script, runInNewContext } from 'node:vm'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const read = path => readFileSync(resolve(root, path), 'utf8').replace(/^\uFEFF/, '')
function files(path) {
  return readdirSync(path, { withFileTypes: true }).flatMap(entry => {
    const child = resolve(path, entry.name)
    return entry.isDirectory() ? files(child) : [child]
  })
}
const javaFiles = files(resolve(root, 'backend/src/main/java')).filter(path => path.endsWith('.java'))
const constants = new Map()
for (const path of javaFiles) {
  const cls = path.split(/[\\/]/).at(-1).replace('.java', '')
  for (const match of readFileSync(path, 'utf8').matchAll(/(?:public |private |protected )?static final String (\w+)\s*=\s*([^;]+);/g)) {
    constants.set(`${cls}.${match[1]}`, { expression: match[2], cls })
  }
}
function expression(text, cls, depth = 0) {
  assert.ok(depth < 12, 'Unresolved Java route constant')
  return text.trim().split(/\s*\+\s*/).map(part => {
    if (/^"[^"]*"$/.test(part)) return JSON.parse(part)
    const found = constants.get(part.includes('.') ? part : `${cls}.${part}`)
    assert.ok(found, `Unknown route expression ${cls}: ${part}`)
    return expression(found.expression, found.cls, depth + 1)
  }).join('')
}
function mappingPaths(args = '', cls) {
  const path = args.match(/(?:^|,)\s*(?:value|path)\s*=\s*(\{[^}]*\}|[^,]+)/)?.[1]
    ?? (/^\s*(?:"|\{|[A-Z]\w*(?:\.[A-Z_]*)?\s*(?:\+|$))/.test(args) ? args.split(/,\s*(?:consumes|produces|headers|params)\s*=/)[0] : '')
  if (!path) return ['']
  return path.replace(/^\s*\{|\}\s*$/g, '').split(',').map(value => expression(value, cls))
}
const routes = []
for (const path of javaFiles.filter(file => file.endsWith('Controller.java'))) {
  const source = readFileSync(path, 'utf8').replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, '')
  const cls = path.split(/[\\/]/).at(-1).replace('.java', '')
  const base = mappingPaths(source.match(/@RequestMapping\(([^)]*)\)/)?.[1], cls)
  for (const match of source.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping\b(?:\(([^)]*)\))?/g)) {
    for (const prefix of base) for (const suffix of mappingPaths(match[2], cls)) {
      routes.push({ method: match[1].toUpperCase(), path: prefix + suffix,
        source: relative(root, path).replaceAll('\\', '/'), devOnly: source.includes('@Profile(') })
    }
  }
}
routes.sort((a, b) => `${a.path} ${a.method}`.localeCompare(`${b.path} ${b.method}`))
if (process.argv.includes('--routes')) {
  console.log(JSON.stringify(routes, null, 2))
  process.exit(0)
}
const collection = JSON.parse(read('postman/SwiftDrop_API_Tests.postman_collection.json'))
const environment = JSON.parse(read('postman/SwiftDrop_Local.postman_environment.json'))
assert.equal(collection.info.schema, 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json')
const requests = [], scripts = []
function walk(item, folder = '') {
  for (const event of item.event ?? []) {
    const code = event.script.exec.join('\n')
    new Script(code, { filename: `${folder}/${item.name ?? 'collection'}:${event.listen}` })
    scripts.push({ code, event: event.listen })
  }
  if (item.request) requests.push({ ...item, folder })
  for (const child of item.item ?? []) walk(child, item.request ? folder : `${folder}/${item.name ?? ''}`)
}
walk(collection)
const names = requests.map(item => item.name)
assert.equal(new Set(names).size, names.length, 'Request names must be unique')
const normalize = url => String(typeof url === 'string' ? url : url.raw).replace('{{baseUrl}}', '').split('?')[0]
const routeRegex = route => new RegExp('^' + route.path.replace(/[.*+?^$()|[\]\\]/g, '\\$&').replace(/\{[^}]+\}/g, '[^/]+') + '$')
const coverage = new Map(routes.map(route => [`${route.method} ${route.path}`, []]))
const known = new Set([...environment.values.map(value => value.key), ...(collection.variable ?? []).map(value => value.key)])
for (const script of scripts) for (const match of script.code.matchAll(/pm\.(?:variables|environment)\.set\(["']([^"']+)/g)) known.add(match[1])
for (const item of requests) {
  assert.deepEqual(item.response, [], `${item.name}: saved response examples can contain secrets`)
  const matches = routes.filter(route => route.method === item.request.method && routeRegex(route).test(normalize(item.request.url)))
  assert.ok(matches.length, `Unmatched backend route: ${item.name}`)
  for (const route of matches) coverage.get(`${route.method} ${route.path}`).push(item)
  assert.ok(item.event?.some(event => event.listen === 'prerequest'), `${item.name}: missing prerequisites/status expectation`)
  for (const match of JSON.stringify(item.request).matchAll(/\{\{([^{}]+)\}\}/g)) {
    assert.ok(match[1].startsWith('$') || known.has(match[1]), `${item.name}: unknown variable ${match[1]}`)
  }
  const body = item.request.body
  if (body?.mode === 'raw' && !item.name.toLowerCase().includes('malformed json')) JSON.parse(body.raw)
  const apiKey = item.request.header.find(header => header.key.toLowerCase() === 'x-api-key')
  if (apiKey) assert.equal(item.request.auth.type, 'noauth', `${item.name}: don't mix API key and bearer identity`)
}
const values = Object.fromEntries(environment.values.map(value => [value.key, value.value]))
for (const value of environment.values) {
  if (!/^(?:allow|run|strict|testEnvironmentConfirmed)/.test(value.key) && /password|token|apiKey|signingSecret|verificationCode|resetCode/i.test(value.key)) {
    assert.equal(value.value, '', `Committed sensitive default: ${value.key}`)
    assert.equal(value.type, 'secret', `Missing secret marker: ${value.key}`)
  }
  if (/^(?:allow|run|strict|testEnvironmentConfirmed)/.test(value.key)) assert.equal(value.value, 'false', `${value.key} must be opt-in`)
}
const globalPre = collection.event.find(event => event.listen === 'prerequest').script.exec.join('\n')
const skip = Symbol('skip')
function preflight(item, overrides = {}, runRequest = true) {
  const data = { ...values, ...overrides }, local = {}
  let body = item.request.body?.raw
  const scope = { get: key => local[key] ?? data[key], set: (key, value) => { local[key] = value }, unset: key => { delete data[key]; delete local[key] } }
  const pm = { variables: { ...scope, replaceIn: value => value.replace(/\{\{([^}]+)\}\}/g, (_, key) => key === '$guid' ? '12345678-1234-4234-8234-123456789abc' : String(scope.get(key) ?? '')) },
    environment: { ...scope, set: (key, value) => { data[key] = value } },
    execution: { skipRequest: () => { throw skip } },
    request: { method: item.request.method, url: { toString: () => item.request.url },
      body: body === undefined ? undefined : { raw: body, update: value => { body = value } } } }
  try {
    runInNewContext(globalPre, { pm, console: { warn() {}, log() {} }, URL })
    if (runRequest) for (const event of item.event.filter(event => event.listen === 'prerequest')) {
      runInNewContext(event.script.exec.join('\n'), { pm, console: { warn() {}, log() {} }, URL })
    }
    return { skipped: false, data, local, body }
  } catch (error) {
    if (error === skip) return { skipped: true, data, local, body }
    throw error
  }
}
const first = requests[0]
assert.throws(() => preflight(first, { baseUrl: 'https://production.invalid' }, false), /test|host|local/i)
assert.throws(() => preflight(first, { baseUrl: 'http://localhost:8080/' }, false), /baseUrl/)
assert.throws(() => preflight(first, { baseUrl: 'https://user:pass@localhost' }, false), /baseUrl/)
preflight(first, { baseUrl: 'http://localhost:8080' }, false)
preflight(first, { baseUrl: 'https://testing.invalid', allowedTestHost: 'testing.invalid', testEnvironmentConfirmed: 'true' }, false)
for (const item of requests) preflight(item) // Default guards must be executable, including skipped workflows.
const writes = requests.filter(item => /^\[(?:WRITE|ROTATE|OUTBOUND|STATE CHANGE)/.test(item.name))
for (const item of writes) assert.ok(preflight(item).skipped, `Unsafe default write: ${item.name}`)
const login = requests.find(item => item.name === 'Login customer and save tokens')
const quotePassword = 'Test"\\Pass!123'
const result = preflight(login, { customerEmail: 'test@example.test', customerPassword: quotePassword })
assert.equal(JSON.parse(result.body).password, quotePassword, 'Resolve values before JSON serialization')
const productionWrite = writes[0]
assert.ok(preflight(productionWrite, { allowWrites: 'true' }).skipped, 'Writing requires dedicated-test confirmation')
// Execute positive prerequisite branches with synthetic values. These are NOT response tests.
const synthetic = Object.fromEntries(environment.values.map(value => [value.key,
  /^(?:allow|run|strict|testEnvironmentConfirmed)/.test(value.key) ? 'true' : '12345678-1234-4234-8234-123456789abc']))
Object.assign(synthetic, { baseUrl: 'http://localhost:8080', onboardingEmail: 'applicant@example.test',
  onboardingEmailSnapshot: 'applicant@example.test', customerEmail: 'customer@example.test',
  customerPassword: quotePassword, newPassword: 'Different!123', operatorRole: 'ROLE_DISPATCHER',
  onboardingCreatedBySuite: 'true', serviceType: 'STANDARD', pickupCity: 'Colombo', dropoffCity: 'Colombo' })
let enabledBranchSimulations = 0
for (const item of requests) for (const phase of ['registered', 'verified', 'approved', 'rejected']) {
  const prepared = preflight(item, { ...synthetic, onboardingPhase: phase })
  enabledBranchSimulations++
  if (!prepared.skipped && prepared.body && !item.name.toLowerCase().includes('malformed json')) JSON.parse(prepared.body)
}
for (const name of ['[WRITE] Create merchant machine order with an idempotency key',
  '[WRITE] Sequential idempotent replay returns the original order',
  '[ROTATE] Plural explicit webhook secret rotation',
  '[OUTBOUND] Send a signed test ping to the saved receiver']) {
  assert.equal(preflight(requests.find(item => item.name === name), synthetic).skipped, false, `Enabled workflow unexpectedly skipped: ${name}`)
}
const signup = requests.find(item => item.name === '[WRITE] Register a fresh merchant application')
assert.equal(preflight(signup, { ...synthetic, onboardingUserId: '', onboardingPhase: '', onboardingEmailSnapshot: '' }).skipped, false)
const approval = requests.find(item => item.name === '[STATE CHANGE] Approve only this suite merchant application')
assert.equal(preflight(approval, { ...synthetic, onboardingPhase: 'verified' }).skipped, false)
assert.throws(() => preflight(approval, { ...synthetic, onboardingPhase: 'verified', onboardingCreatedBySuite: '' }), /suite signup/)
assert.throws(() => preflight(approval, { ...synthetic, onboardingPhase: 'verified', onboardingEmailSnapshot: 'another@example.test' }), /markers/)
const verification = requests.find(item => item.name === '[WRITE] Verify applicant email using its fresh inbox code')
assert.equal(preflight(verification, { ...synthetic, onboardingPhase: 'approved' }).skipped, true, 'Do not re-verify an already approved account')
for (const item of writes) for (const event of item.event) {
  const code = event.script.exec.join('\n')
  assert.ok(!/pm\.environment\.set\(["'](?:\w*(?:AccessToken|RefreshToken|ApiKey|SigningSecret)|accessToken|refreshToken|newSigningSecret)["']/.test(code),
    `${item.name}: sensitive runtime capture must not persist in exported environment`)
}
// Whitelist JSON property names against the actual core DTO fields, not a copied OpenAPI document.
const dtoCases = [
  [/^\/api\/v1\/auth\/signup$/, 'POST', 'auth/dto/SignUpRequest.java'],
  [/^\/api\/v1\/auth\/change-password$/, 'POST', 'auth/dto/ChangePasswordRequest.java'],
  [/^\/api\/v1\/merchant(?:\/api\/v1)?\/orders$/, 'POST', 'merchant/dto/CreateMerchantOrderRequest.java'],
  [/^\/api\/v1\/merchant(?:\/api\/v1)?\/calculate-fee$/, 'POST', 'merchant/dto/FeeCalculationRequest.java'],
  [/^\/api\/v1\/merchant\/register$/, 'POST', 'merchant/dto/MerchantRegisterRequest.java'],
  [/^\/api\/v1\/merchant\/(?:webhook|webhooks\/config)$/, 'PUT', 'webhook/dto/WebhookConfigurationRequest.java'],
  [/^\/api\/v1\/merchant\/webhook\/test$/, 'POST', 'merchant/dto/WebhookTestRequest.java'],
  [/^\/api\/v1\/admin\/merchants\/[^/]+\/(?:approve|reject)$/, 'POST', 'merchant/dto/MerchantApprovalRequest.java'],
]
let dtoPayloadChecks = 0
for (const item of requests) {
  if (item.request.body?.mode !== 'raw' || item.name.toLowerCase().includes('malformed json')) continue
  const path = normalize(item.request.url)
  const spec = dtoCases.find(([pattern, method]) => pattern.test(path) && method === item.request.method)
  if (!spec) continue
  const source = read('backend/src/main/java/com/swiftdrop/backend/modules/' + spec[2])
  const fields = new Set([...source.matchAll(/private\s+[\w<>]+\s+(\w+)\s*;/g)].map(match => match[1]))
  if (spec[2].includes('WebhookConfigurationRequest')) {
    assert.ok(source.includes('JsonAlias("webhookUrl")'))
    fields.add('url'); fields.add('webhookUrl')
  }
  // These extra fields are deliberate ownership/secret tampering probes; the server ignores them.
  for (const field of ['merchantId', 'userId', 'secret', 'webhookSecret']) fields.add(field)
  for (const field of Object.keys(JSON.parse(item.request.body.raw))) assert.ok(fields.has(field), `${item.name}: unknown DTO property ${field}`)
  dtoPayloadChecks++
}
const productionRoutes = routes.filter(route => !route.devOnly)
const covered = productionRoutes.filter(route => coverage.get(`${route.method} ${route.path}`).length)
assert.equal(covered.length, productionRoutes.length, 'Every production REST route needs a prepared request (auth smoke is explicit)')
const testBlocks = scripts.reduce((n, script) => n + (script.code.match(/\bpm\.test\s*\(/g) ?? []).length, 0)
const expectChecks = scripts.reduce((n, script) => n + (script.code.match(/\bpm\.expect\s*\(/g) ?? []).length, 0)
const summary = { requests: requests.length, scriptSyntaxChecks: scripts.length, staticTestBlocks: testBlocks,
  staticExpectChecks: expectChecks, productionRouteVariants: productionRoutes.length, coveredRouteVariants: covered.length,
  authOnlyRouteVariants: productionRoutes.filter(route => coverage.get(`${route.method} ${route.path}`).every(item => item.name.startsWith('[AUTH SMOKE]'))).length,
  dtoPayloadChecks, defaultPrerequestSimulations: requests.length, enabledBranchSimulations,
  guardedWriteSimulations: writes.length, liveHttpRequests: 0 }
if (process.argv.includes('--write-coverage')) {
  const lines = ['# Endpoint coverage', '', 'Generated offline from current Spring controller mappings by `node postman/validate.mjs --write-coverage`.',
    'A prepared request is not a live test result. Auth-only rows verify a security boundary, not business behavior.', '',
    '| Method | Route | Prepared cases | Coverage | Controller |', '|---|---|---:|---|---|']
  for (const route of routes) {
    const cases = coverage.get(`${route.method} ${route.path}`)
    const substantive = cases.some(item => !item.name.startsWith('[AUTH SMOKE]'))
    lines.push(`| ${route.method} | \`${route.path}\` | ${cases.length} | ${route.devOnly ? 'Development-only; intentionally excluded' : substantive ? 'Contract/lifecycle cases' : 'Anonymous authentication smoke only'} | [${route.source.split('/').at(-1)}](../${route.source}) |`)
  }
  writeFileSync(resolve(root, 'postman/endpoint-coverage.md'), lines.join('\n') + '\n')
}
console.log(JSON.stringify(summary, null, 2))
console.log('PASS: JSON, script compilation, source route matching, variable references, secret defaults, guard simulations and JSON escaping. No network calls made.')
