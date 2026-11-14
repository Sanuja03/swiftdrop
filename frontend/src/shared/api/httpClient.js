import { clearSession, getAccessToken, getRefreshToken, saveSession } from '@/shared/auth/session'

/**
 * The one HTTP client for the whole frontend.
 *
 * - Base URL comes from VITE_API_BASE_URL (e.g. https://api.example.com). Leave it empty to
 *   call the same origin, which the Vite dev proxy and the nginx container forward to the backend.
 * - Every request sends the signed-in user's JWT.
 * - On 401 it refreshes the JWT once with the refresh token and retries; if that fails the
 *   session is cleared and the browser goes to /login.
 */
const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')
const API_BASE_URL = `${API_ORIGIN}/api/v1`
const LOGIN_PATH = '/login'

/** Full URL of an API path, e.g. apiUrl('/zones'). */
export function apiUrl(path) {
  return `${API_BASE_URL}${path}`
}

/** WebSocket URL of a backend path (same host as the API), e.g. webSocketUrl('/ws'). */
export function webSocketUrl(path) {
  const base = new URL(API_ORIGIN || window.location.origin, window.location.origin)
  base.protocol = base.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${base.origin}${path}`
}

/** An error response from the API, carrying RFC 9457 problem details or the auth module's error body. */
export class ApiError extends Error {
  constructor(status, title, detail, body = null) {
    super(detail || title || `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.body = body
  }

  get isNotFound() {
    return this.status === 404
  }

  get isUnauthorized() {
    return this.status === 401 || this.status === 403
  }
}

async function toApiError(response) {
  try {
    const body = await response.json()
    return new ApiError(response.status, body.title ?? body.error, body.detail ?? body.message, body)
  } catch {
    return new ApiError(response.status, response.statusText)
  }
}

let refreshInFlight = null

/** Swaps the refresh token for a new JWT. Concurrent 401s share one refresh call. */
function refreshAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return Promise.resolve(false)
  if (!refreshInFlight) {
    refreshInFlight = fetch(apiUrl('/auth/refresh'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
      .then(async (response) => {
        if (!response.ok) return false
        saveSession(await response.json())
        return true
      })
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

function signOutAndGoToLogin() {
  clearSession()
  if (window.location.pathname !== LOGIN_PATH) {
    window.location.assign(LOGIN_PATH)
  }
}

async function send(path, init, headers) {
  const token = getAccessToken()
  const allHeaders = { ...headers }
  if (token) allHeaders.Authorization = `Bearer ${token}`
  try {
    return { response: await fetch(apiUrl(path), { ...init, headers: allHeaders }), sentToken: Boolean(token) }
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new ApiError(0, 'Network error', 'Cannot reach SwiftDrop right now. Check your connection and try again.')
  }
}

/**
 * Calls the backend and returns the parsed body (null for 204).
 *
 * @param {string} path API path after /api/v1, e.g. '/zones'
 * @param {object} [options]
 * @param {'json' | 'blob'} [options.responseType]
 * @param {unknown} [options.json] request body, sent as JSON
 * @param {boolean} [options.redirectOnUnauthorized] set false for sign-in calls, where 401 means wrong credentials
 */
export async function request(
  path,
  { responseType = 'json', json, headers = {}, redirectOnUnauthorized = true, ...init } = {},
) {
  const requestInit = json === undefined ? init : { method: 'POST', ...init, body: JSON.stringify(json) }
  const requestHeaders = json === undefined ? headers : { 'Content-Type': 'application/json', ...headers }

  let { response, sentToken } = await send(path, requestInit, requestHeaders)

  if (response.status === 401 && redirectOnUnauthorized && sentToken) {
    if (await refreshAccessToken()) {
      ;({ response } = await send(path, requestInit, requestHeaders))
    }
    if (response.status === 401) {
      signOutAndGoToLogin()
    }
  }

  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return null
  return responseType === 'blob' ? response.blob() : response.json()
}
