import { request } from './httpClient'

/** Downloads a parcel's PDF receipt as a Blob. */
export function getReceipt(trackingNumber) {
  return request(`/receipts/${encodeURIComponent(trackingNumber)}`, { responseType: 'blob' })
}
