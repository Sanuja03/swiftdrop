import { request } from '@/shared/api/httpClient'

/**
 * Merchant portal API. Every call goes through the shared HTTP client, which sends the signed-in
 * user's JWT; the backend works out the merchant from that token.
 *
 * The `merchantId` parameters are kept so existing callers do not change, but they are ignored:
 * the backend never accepts a merchant id from the browser. Errors are thrown (ApiError with the
 * server's message) instead of being replaced by demo data.
 */
/* eslint-disable no-unused-vars */
const BASE = '/merchant'

function query(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.append(key, value)
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

export const merchantApi = {
  /** The signed-in merchant's own profile. A 404 means the account has no merchant profile yet. */
  getProfile(merchantId) {
    return request(`${BASE}/profile`)
  },

  getStats(merchantId) {
    return request(`${BASE}/stats`)
  },

  getOrders(merchantId, status, search) {
    return request(`${BASE}/orders${query({ status: status && status !== 'ALL' ? status : undefined, search })}`)
  },

  createOrder(merchantId, orderData) {
    return request(`${BASE}/orders`, { json: orderData })
  },

  cancelOrder(merchantId, trackingNumber) {
    return request(`${BASE}/orders/${encodeURIComponent(trackingNumber)}/cancel`, { method: 'POST' })
  },

  /** Fee from the shared pricing rules; pickup and drop-off cities must be active delivery zones. */
  calculateFee(feeRequest) {
    return request(`${BASE}/calculate-fee`, { json: feeRequest })
  },

  updateWebhook(merchantId, webhookData) {
    return request(`${BASE}/webhook`, { method: 'PUT', json: webhookData })
  },

  testWebhook(merchantId, testUrl) {
    return request(`${BASE}/webhook/test`, { json: { webhookUrl: testUrl } })
  },

  getWebhookLogs(merchantId) {
    return request(`${BASE}/webhook/logs`)
  },

  /** Returns { apiKey } once; the key is not shown again until it is regenerated. */
  regenerateApiKey(merchantId) {
    return request(`${BASE}/api-key/regenerate`, { method: 'POST' })
  },

  /** Creates the merchant profile for the signed-in merchant user's own account. */
  registerMerchant(registrationData) {
    return request(`${BASE}/register`, { json: registrationData })
  },

  // Admin only (ROLE_ADMIN): merchant approval
  listAdminMerchants(status) {
    return request(`/admin/merchants${query({ status })}`)
  },

  approveMerchant(merchantId, notes) {
    return request(`/admin/merchants/${encodeURIComponent(merchantId)}/approve`, { json: { notes } })
  },

  rejectMerchant(merchantId, notes) {
    return request(`/admin/merchants/${encodeURIComponent(merchantId)}/reject`, { json: { notes } })
  },
}
