import { request, ApiError } from '../../../shared/api/httpClient.js'

export { ApiError }

/**
 * Builds standard JSON headers including the current JWT bearer token.
 * Reuses the existing SwiftDrop authentication storage pattern.
 *
 * @returns {Record<string, string>}
 */
function getAuthHeaders() {
  const token = localStorage.getItem('swiftdrop_token')
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

/**
 * Retrieves parcels with status CREATED that have no active courier assignment.
 * Backend endpoint: GET /api/v1/dispatcher/parcels/unassigned
 *
 * @param {object} [options]
 * @param {AbortSignal} [options.signal]
 * @returns {Promise<Array<{
 *   id: string,
 *   trackingNumber: string,
 *   senderName: string,
 *   recipientName: string,
 *   recipientPhone: string,
 *   pickupAddress: string,
 *   pickupCity: string,
 *   dropoffAddress: string,
 *   dropoffCity: string,
 *   serviceType: 'STANDARD' | 'EXPRESS' | 'SAME_DAY',
 *   weightKg: number,
 *   fee: number,
 *   status: string,
 *   estimatedDeliveryAt: string|null,
 *   createdAt: string
 * }>>}
 */
export async function getUnassignedParcels({ signal } = {}) {
  return request('/dispatcher/parcels/unassigned', {
    method: 'GET',
    headers: getAuthHeaders(),
    signal,
  })
}

/**
 * Retrieves all active users with role ROLE_COURIER.
 * Backend endpoint: GET /api/v1/dispatcher/couriers/active
 *
 * @param {object} [options]
 * @param {AbortSignal} [options.signal]
 * @returns {Promise<Array<{
 *   id: string,
 *   fullName: string,
 *   email: string,
 *   phone: string
 * }>>}
 */
export async function getActiveCouriers({ signal } = {}) {
  return request('/dispatcher/couriers/active', {
    method: 'GET',
    headers: getAuthHeaders(),
    signal,
  })
}

/**
 * Assigns an unassigned parcel to an active courier.
 * Backend endpoint: POST /api/v1/dispatcher/assignments
 *
 * @param {object} payload
 * @param {string} payload.parcelId UUID of the parcel to assign
 * @param {string} payload.courierId UUID of the courier
 * @param {string} [payload.notes] Optional dispatcher notes (max 500 chars)
 * @param {object} [options]
 * @param {AbortSignal} [options.signal]
 * @returns {Promise<{
 *   id: string,
 *   parcelId: string,
 *   courierId: string,
 *   dispatcherId: string,
 *   status: string,
 *   notes: string|null,
 *   assignedAt: string,
 *   updatedAt: string,
 *   version: number
 * }>>}
 */
export async function assignCourier({ parcelId, courierId, notes }, { signal } = {}) {
  if (!parcelId) throw new Error('parcelId is required')
  if (!courierId) throw new Error('courierId is required')

  const body = {
    parcelId,
    courierId,
    ...(notes && notes.trim() ? { notes: notes.trim() } : {}),
  }

  return request('/dispatcher/assignments', {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify(body),
    signal,
  })
}

/**
 * Retrieves current active workloads for all active couriers.
 * Backend endpoint: GET /api/v1/dispatcher/workloads
 *
 * @param {object} [options]
 * @param {AbortSignal} [options.signal]
 * @returns {Promise<Array<{
 *   courierId: string,
 *   courierName: string,
 *   courierEmail: string,
 *   courierPhone: string,
 *   activeAssignmentsCount: number,
 *   workloadStatus: 'AVAILABLE' | 'MODERATE' | 'BUSY'
 * }>>}
 */
export async function getCourierWorkloads({ signal } = {}) {
  return request('/dispatcher/workloads', {
    method: 'GET',
    headers: getAuthHeaders(),
    signal,
  })
}

/**
 * Cancels an active courier assignment.
 * Backend endpoint: PATCH /api/v1/dispatcher/assignments/{assignmentId}/cancel
 *
 * @param {string} assignmentId UUID of the assignment to cancel
 * @param {object} [payload]
 * @param {string} [payload.reason] Optional cancellation reason
 * @param {object} [options]
 * @param {AbortSignal} [options.signal]
 * @returns {Promise<{
 *   id: string,
 *   parcelId: string,
 *   courierId: string,
 *   dispatcherId: string,
 *   status: string,
 *   notes: string|null,
 *   assignedAt: string,
 *   updatedAt: string,
 *   version: number
 * }>>}
 */
export async function cancelAssignment(assignmentId, { reason, signal } = {}) {
  if (!assignmentId) throw new Error('assignmentId is required')

  const body = {
    ...(reason && reason.trim() ? { reason: reason.trim() } : {}),
  }

  return request(`/dispatcher/assignments/${encodeURIComponent(assignmentId)}/cancel`, {
    method: 'PATCH',
    headers: getAuthHeaders(),
    body: JSON.stringify(body),
    signal,
  })
}
