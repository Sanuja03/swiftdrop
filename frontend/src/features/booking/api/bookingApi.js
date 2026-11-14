import { request } from '@/shared/api/httpClient'

/** Active delivery zones: [{ id, code, name, surcharge }]. Public. */
export function getZones({ signal } = {}) {
  return request('/zones', { signal })
}

/**
 * Fee estimate from the pricing service. Public.
 * @returns {Promise<{ baseFee, weightCharge, zoneCharge, totalFee, currency, estimatedDeliveryAt }>}
 */
export function estimateFee({ pickupZoneId, dropoffZoneId, parcelSize, weightKg, serviceType }, { signal } = {}) {
  return request('/parcels/estimate', {
    signal,
    json: { pickupZoneId, dropoffZoneId, parcelSize, weightKg, serviceType },
  })
}

/**
 * Books a delivery for the signed-in customer.
 * @returns {Promise<{ parcelId, trackingNumber, status, totalFee, currency, createdAt, estimatedDeliveryAt }>}
 */
export function createParcel(booking) {
  const {
    pickupZoneId,
    dropoffZoneId,
    parcelSize,
    weightKg,
    serviceType,
    pickupAddress,
    dropoffAddress,
    recipientName,
    recipientPhone,
  } = booking
  return request('/parcels', {
    json: {
      pickupZoneId,
      dropoffZoneId,
      parcelSize,
      weightKg,
      serviceType,
      pickupAddress,
      dropoffAddress,
      recipientName,
      recipientPhone,
    },
  })
}

/** One of the signed-in customer's bookings, with addresses and fee breakdown. */
export function getBooking(trackingNumber, { signal } = {}) {
  return request(`/parcels/${encodeURIComponent(trackingNumber)}`, { signal })
}

/** Starting price of each active service: [{ serviceType, fromPrice, currency }]. Public. */
export function getServicePrices({ signal } = {}) {
  return request('/pricing/services', { signal })
}
