import { request } from '@/shared/api/httpClient'

/** Public tracking details (no login needed). */
export function getPublicTracking(trackingNumber, { signal } = {}) {
  return request(`/tracking/public/${encodeURIComponent(trackingNumber)}`, { signal })
}
