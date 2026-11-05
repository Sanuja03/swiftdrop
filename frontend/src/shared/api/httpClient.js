const API_BASE_URL = '/api/v1'

/** An error response from the API, carrying the RFC 9457 problem details when present. */
export class ApiError extends Error {
  constructor(status, title, detail) {
    super(detail || title || `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.title = title
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
    return new ApiError(response.status, body.title ?? body.error, body.detail ?? body.message)
  } catch {
    return new ApiError(response.status, response.statusText)
  }
}

/**
 * Calls the backend and returns the parsed body.
 * @param {'json' | 'blob'} [responseType]
 */
export async function request(path, { signal, responseType = 'json', ...init } = {}) {
  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { signal, ...init })
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new ApiError(0, 'Network error', 'Cannot reach SwiftDrop right now. Check your connection and try again.')
  }

  if (!response.ok) throw await toApiError(response)
  return responseType === 'blob' ? response.blob() : response.json()
}
