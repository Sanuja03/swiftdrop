import assert from 'node:assert/strict'
import { after, afterEach, before, beforeEach, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { createServer } from 'vite'
import { reportPeriod } from './reportPeriods.js'

const root = fileURLToPath(new URL('../../../../', import.meta.url))
let server
let reports
let users
let merchants
let notifications
let storage
let redirects
const originalFetch = globalThis.fetch
const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window')
const originalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')

before(async () => {
  server = await createServer({
    configFile: false,
    root,
    cacheDir: path.join(root, 'node_modules/.vite-feature-tests'),
    optimizeDeps: { noDiscovery: true },
    resolve: { alias: { '@': path.join(root, 'src') } },
    server: { middlewareMode: true, hmr: false },
    define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('https://api.example.test') },
  })
  reports = await server.ssrLoadModule('/src/features/reports/services/reportApi.js')
  users = await server.ssrLoadModule('/src/features/users/services/userManagementApi.js')
  merchants = await server.ssrLoadModule('/src/features/users/services/merchantApprovalApi.js')
  notifications = await server.ssrLoadModule('/src/features/notifications/services/notificationApi.js')
})

beforeEach(() => {
  storage = new Map([['swiftdrop_token', 'expired-test-token'], ['swiftdrop_refresh_token', 'test-refresh']])
  redirects = []
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
  } })
  Object.defineProperty(globalThis, 'window', { configurable: true, value: {
    location: { pathname: '/admin/reports', assign: (url) => redirects.push(url) },
  } })
})

afterEach(() => {
  globalThis.fetch = originalFetch
  if (originalWindow) Object.defineProperty(globalThis, 'window', originalWindow)
  else delete globalThis.window
  if (originalStorage) Object.defineProperty(globalThis, 'localStorage', originalStorage)
  else delete globalThis.localStorage
})

after(async () => server?.close())

test('concurrent feature calls share one refresh and use the configured API origin', async () => {
  let refreshes = 0
  const requests = []
  globalThis.fetch = async (url, options = {}) => {
    requests.push({ url, options })
    assert.ok(url.startsWith('https://api.example.test/api/v1/'))
    if (url.endsWith('/auth/refresh')) {
      refreshes++
      return Response.json({ accessToken: 'fresh-test-token', refreshToken: 'rotated-test-refresh' })
    }
    if (options.headers.Authorization !== 'Bearer fresh-test-token') return Response.json({}, { status: 401 })
    if (url.endsWith('/summary')) return Response.json({ totalParcels: 7 })
    return Response.json([])
  }
  const [summary, userList, notificationList, merchantList] = await Promise.all([
    reports.getReportSummary(), users.getAllUsers(), notifications.getMyNotifications(), merchants.getMerchantApplications(),
  ])
  assert.equal(summary.totalParcels, 7)
  assert.deepEqual(userList, [])
  assert.deepEqual(notificationList, [])
  assert.deepEqual(merchantList, [])
  assert.equal(refreshes, 1)
  assert.equal(storage.get('swiftdrop_token'), 'fresh-test-token')
  assert.deepEqual(redirects, [])
  assert.equal(requests.filter((item) => item.options.headers?.Authorization === 'Bearer fresh-test-token').length, 4)
})

test('a rejected refresh clears the session and sends the user to login', async () => {
  globalThis.fetch = async () => Response.json({ message: 'Expired test session' }, { status: 401 })
  await assert.rejects(reports.getReportSummary(), (error) => error.status === 401)
  assert.equal(storage.has('swiftdrop_token'), false)
  assert.equal(storage.has('swiftdrop_refresh_token'), false)
  assert.deepEqual(redirects, ['/login'])
})

test('forbidden responses keep their status without refreshing or signing out', async () => {
  let requests = 0
  globalThis.fetch = async () => {
    requests++
    return Response.json({ detail: 'Permission denied' }, { status: 403 })
  }
  await assert.rejects(users.getAllUsers(), (error) => error.status === 403 && error.message === 'Permission denied')
  assert.equal(requests, 1)
  assert.equal(storage.get('swiftdrop_token'), 'expired-test-token')
  assert.deepEqual(redirects, [])
})

test('snapshot retries preserve the same request ID and merchant/notification writes keep their contracts', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    return Response.json({ id: 'test-id' })
  }
  const snapshot = { id: '11111111-1111-4111-8111-111111111111', startDate: '2026-11-01', endDate: '2026-11-07' }
  await reports.saveReportSnapshot(snapshot)
  await reports.saveReportSnapshot(snapshot)
  await merchants.rejectMerchantApplication('merchant/id', 'Review notes')
  await notifications.markNotificationAsRead('notification/id')
  assert.deepEqual(JSON.parse(calls[0].options.body), snapshot)
  assert.equal(calls[0].options.body, calls[1].options.body)
  assert.ok(calls[2].url.endsWith('/admin/merchants/merchant%2Fid/reject'))
  assert.deepEqual(JSON.parse(calls[2].options.body), { notes: 'Review notes' })
  assert.equal(calls[3].options.method, 'PATCH')
  assert.ok(calls[3].url.endsWith('/notifications/notification%2Fid/read'))
})

test('outcome, daily and saved-date filters use independent query parameters', async () => {
  const calls = []
  globalThis.fetch = async (url) => { calls.push(new URL(url)); return Response.json([]) }
  const outcome = reportPeriod(30, '2026-11-18')
  const daily = reportPeriod(7, '2026-11-18')
  const saved = reportPeriod(7, '2026-11-10')
  await reports.getSuccessRate({}, outcome)
  await reports.getDailyDeliveries(daily.startDate, daily.endDate)
  await reports.getReportSnapshots(2, {}, saved)
  assert.equal(calls[0].searchParams.get('startDate'), '2026-10-20')
  assert.equal(calls[1].searchParams.get('startDate'), '2026-11-12')
  assert.equal(calls[2].searchParams.get('startDate'), '2026-11-04')
  assert.equal(calls[2].searchParams.get('page'), '2')
  assert.equal(calls[2].searchParams.get('size'), '5')
  await reports.getSuccessRate()
  await reports.getReportSnapshots()
  assert.equal(calls[3].search, '')
  assert.equal(calls[4].searchParams.has('startDate'), false)
  assert.equal(reportPeriod(0, '2026-11-18'), null)
})

test('snapshot request preserves its independently selected outcome range', async () => {
  let body
  globalThis.fetch = async (_url, options) => { body = JSON.parse(options.body); return Response.json(body) }
  const request = { id: 'test-id', ...reportPeriod(7, '2026-11-18'), successPeriod: reportPeriod(30, '2026-11-18') }
  await reports.saveReportSnapshot(request)
  assert.deepEqual(body, request)
})
