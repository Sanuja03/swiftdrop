import { describe, it, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import {
  getUnassignedParcels,
  getActiveCouriers,
  assignCourier,
  getCourierWorkloads,
  cancelAssignment,
  ApiError,
} from '../api/dispatchApi.js'

describe('Dispatcher API Client - Unit Tests', () => {
  let mockFetchCalls = []
  let storage = {}

  beforeEach(() => {
    mockFetchCalls = []
    storage = {}

    globalThis.localStorage = {
      getItem: (key) => storage[key] || null,
      setItem: (key, val) => {
        storage[key] = String(val)
      },
      removeItem: (key) => {
        delete storage[key]
      },
      clear: () => {
        storage = {}
      },
    }

    // Default authenticated token in localStorage
    storage['swiftdrop_token'] = 'test-dispatcher-jwt-token'

    // Mock global fetch
    globalThis.fetch = async (url, options = {}) => {
      mockFetchCalls.push({ url, options })

      // Default mock responses
      if (url.endsWith('/dispatcher/parcels/unassigned')) {
        return {
          ok: true,
          status: 200,
          json: async () => [
            {
              id: 'parcel-1111',
              trackingNumber: 'SDP1000001',
              senderName: 'Alice',
              recipientName: 'Bob',
              recipientPhone: '0771234567',
              pickupAddress: '123 Main St',
              pickupCity: 'Colombo',
              dropoffAddress: '456 Cross Rd',
              dropoffCity: 'Kandy',
              serviceType: 'EXPRESS',
              weightKg: 2.5,
              fee: 550.0,
              status: 'CREATED',
              estimatedDeliveryAt: '2026-11-15T10:00:00Z',
              createdAt: '2026-11-14T08:00:00Z',
            },
          ],
        }
      }

      if (url.endsWith('/dispatcher/couriers/active')) {
        return {
          ok: true,
          status: 200,
          json: async () => [
            {
              id: 'courier-2222',
              fullName: 'Sunil Perera',
              email: 'sunil@swiftdrop.lk',
              phone: '0719876543',
            },
          ],
        }
      }

      if (url.endsWith('/dispatcher/assignments') && options.method === 'POST') {
        const body = JSON.parse(options.body)
        return {
          ok: true,
          status: 201,
          json: async () => ({
            id: 'assign-3333',
            parcelId: body.parcelId,
            courierId: body.courierId,
            dispatcherId: 'dispatcher-9999',
            status: 'ASSIGNED',
            notes: body.notes || null,
            assignedAt: '2026-11-14T14:30:00Z',
            updatedAt: '2026-11-14T14:30:00Z',
            version: 1,
          }),
        }
      }

      if (url.endsWith('/dispatcher/workloads')) {
        return {
          ok: true,
          status: 200,
          json: async () => [
            {
              courierId: 'courier-2222',
              courierName: 'Sunil Perera',
              courierEmail: 'sunil@swiftdrop.lk',
              courierPhone: '0719876543',
              activeAssignmentsCount: 2,
              workloadStatus: 'MODERATE',
            },
          ],
        }
      }

      if (url.includes('/cancel') && options.method === 'PATCH') {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            id: 'assign-3333',
            status: 'CANCELLED',
            notes: 'Delivery rescheduled',
          }),
        }
      }

      return {
        ok: false,
        status: 404,
        statusText: 'Not Found',
        json: async () => ({ error: 'Not Found', message: 'Resource not found' }),
      }
    }
  })

  it('1. getUnassignedParcels() calls GET /api/v1/dispatcher/parcels/unassigned with auth header', async () => {
    const parcels = await getUnassignedParcels()

    assert.equal(mockFetchCalls.length, 1)
    const call = mockFetchCalls[0]
    assert.equal(call.url, '/api/v1/dispatcher/parcels/unassigned')
    assert.equal(call.options.method, 'GET')
    assert.equal(call.options.headers['Authorization'], 'Bearer test-dispatcher-jwt-token')
    assert.equal(call.options.headers['Content-Type'], 'application/json')
    assert.equal(parcels.length, 1)
    assert.equal(parcels[0].trackingNumber, 'SDP1000001')
    assert.equal(parcels[0].serviceType, 'EXPRESS')
  })

  it('2. getActiveCouriers() calls GET /api/v1/dispatcher/couriers/active with auth header', async () => {
    const couriers = await getActiveCouriers()

    assert.equal(mockFetchCalls.length, 1)
    const call = mockFetchCalls[0]
    assert.equal(call.url, '/api/v1/dispatcher/couriers/active')
    assert.equal(call.options.method, 'GET')
    assert.equal(call.options.headers['Authorization'], 'Bearer test-dispatcher-jwt-token')
    assert.equal(couriers.length, 1)
    assert.equal(couriers[0].fullName, 'Sunil Perera')
  })

  it('3. assignCourier() validates required fields and sends POST request', async () => {
    // Missing parcelId
    await assert.rejects(
      () => assignCourier({ courierId: 'courier-2222' }),
      /parcelId is required/
    )

    // Missing courierId
    await assert.rejects(
      () => assignCourier({ parcelId: 'parcel-1111' }),
      /courierId is required/
    )

    // Valid assignment
    const result = await assignCourier({
      parcelId: 'parcel-1111',
      courierId: 'courier-2222',
      notes: 'Handle with care',
    })

    assert.equal(mockFetchCalls.length, 1)
    const call = mockFetchCalls[0]
    assert.equal(call.url, '/api/v1/dispatcher/assignments')
    assert.equal(call.options.method, 'POST')
    assert.equal(call.options.headers['Authorization'], 'Bearer test-dispatcher-jwt-token')

    const sentBody = JSON.parse(call.options.body)
    assert.equal(sentBody.parcelId, 'parcel-1111')
    assert.equal(sentBody.courierId, 'courier-2222')
    assert.equal(sentBody.notes, 'Handle with care')

    assert.equal(result.id, 'assign-3333')
    assert.equal(result.status, 'ASSIGNED')
  })

  it('4. getCourierWorkloads() calls GET /api/v1/dispatcher/workloads with auth header', async () => {
    const workloads = await getCourierWorkloads()

    assert.equal(mockFetchCalls.length, 1)
    const call = mockFetchCalls[0]
    assert.equal(call.url, '/api/v1/dispatcher/workloads')
    assert.equal(call.options.method, 'GET')
    assert.equal(call.options.headers['Authorization'], 'Bearer test-dispatcher-jwt-token')
    assert.equal(workloads.length, 1)
    assert.equal(workloads[0].courierName, 'Sunil Perera')
    assert.equal(workloads[0].activeAssignmentsCount, 2)
    assert.equal(workloads[0].workloadStatus, 'MODERATE')
  })

  it('5. cancelAssignment() validates assignmentId and sends PATCH request', async () => {
    await assert.rejects(
      () => cancelAssignment(''),
      /assignmentId is required/
    )

    const result = await cancelAssignment('assign-3333', { reason: 'Delivery rescheduled' })

    assert.equal(mockFetchCalls.length, 1)
    const call = mockFetchCalls[0]
    assert.equal(call.url, '/api/v1/dispatcher/assignments/assign-3333/cancel')
    assert.equal(call.options.method, 'PATCH')
    assert.equal(call.options.headers['Authorization'], 'Bearer test-dispatcher-jwt-token')

    const sentBody = JSON.parse(call.options.body)
    assert.equal(sentBody.reason, 'Delivery rescheduled')
    assert.equal(result.status, 'CANCELLED')
  })

  it('6. Handles 403 Forbidden properly as ApiError', async () => {
    globalThis.fetch = async () => ({
      ok: false,
      status: 403,
      statusText: 'Forbidden',
      json: async () => ({ error: 'Forbidden', message: 'Access Denied: Dispatcher role required' }),
    })

    await assert.rejects(
      () => getUnassignedParcels(),
      (err) => {
        assert(err instanceof ApiError)
        assert.equal(err.status, 403)
        assert.equal(err.isUnauthorized, true)
        assert.match(err.message, /Access Denied/)
        return true
      }
    )
  })

  it('7. Handles missing token gracefully without Authorization header', async () => {
    storage = {} // No token in storage

    await getUnassignedParcels()

    assert.equal(mockFetchCalls.length, 1)
    const call = mockFetchCalls[0]
    assert.equal(call.options.headers['Authorization'], undefined)
  })
})
