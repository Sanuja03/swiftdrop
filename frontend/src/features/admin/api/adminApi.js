import { request } from '@/shared/api/httpClient'

/** Every zone, active or not: [{ id, code, name, surcharge, active, updatedAt }]. */
export function getAllZones({ signal } = {}) {
  return request('/admin/zones', { signal })
}

export function createZone({ code, name, surcharge }) {
  return request('/admin/zones', { json: { code, name, surcharge } })
}

/** Name and surcharge only; a zone's code never changes. */
export function updateZone(id, { name, surcharge }) {
  return request(`/admin/zones/${encodeURIComponent(id)}`, { method: 'PUT', json: { name, surcharge } })
}

export function setZoneActive(id, active) {
  return request(`/admin/zones/${encodeURIComponent(id)}/active`, { method: 'PATCH', json: { active } })
}

/** One rule per service type: [{ serviceType, baseFee, perKgFee, interZoneFee, active, currency, updatedAt }]. */
export function getPricingRules({ signal } = {}) {
  return request('/admin/pricing-rules', { signal })
}

export function updatePricingRule(serviceType, { baseFee, perKgFee, interZoneFee, active }) {
  return request(`/admin/pricing-rules/${encodeURIComponent(serviceType)}`, {
    method: 'PUT',
    json: { baseFee, perKgFee, interZoneFee, active },
  })
}
