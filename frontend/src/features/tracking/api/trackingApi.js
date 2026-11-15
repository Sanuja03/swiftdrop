import { request } from '@/shared/api/httpClient'

/** Public tracking details (no login needed). */
export function getPublicTracking(trackingNumber, { signal } = {}) {
  return request(`/tracking/public/${encodeURIComponent(trackingNumber)}`, { signal })
}

/** The signed-in customer's own shipments, newest booking first. */
export function getMyShipments({ signal } = {}) {
  return request('/customer/parcels', { signal })
}
