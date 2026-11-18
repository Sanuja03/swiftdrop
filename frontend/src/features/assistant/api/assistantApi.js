import { request } from '../../../shared/api/httpClient.js'

function getAuthHeaders() {
  const token = localStorage.getItem('swiftdrop_token')
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

/**
 * Creates a new chat session on the backend for the authenticated customer.
 * @param {object} [options]
 * @param {AbortSignal} [options.signal]
 * @returns {Promise<{ sessionId: string, createdAt: string }>}
 */
export async function createSession({ signal } = {}) {
  return request('/assistant/sessions', {
    method: 'POST',
    headers: getAuthHeaders(),
    signal,
  })
}

/**
 * Sends a customer message to an existing session and receives the AI response.
 * @param {string} sessionId
 * @param {string} message
 * @param {object} [options]
 * @param {AbortSignal} [options.signal]
 * @returns {Promise<{ sessionId: string, answer: string, executedTools: Array, createdAt: string }>}
 */
export async function sendMessage(sessionId, message, { signal } = {}) {
  return request(`/assistant/sessions/${encodeURIComponent(sessionId)}/messages`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ message: message.trim() }),
    signal,
  })
}

/**
 * Retrieves the full conversation history for an existing session.
 * @param {string} sessionId
 * @param {object} [options]
 * @param {AbortSignal} [options.signal]
 * @returns {Promise<Array<{ id: string, sessionId: string, sender: string, content: string, toolName: string|null, createdAt: string }>>}
 */
export async function getSessionHistory(sessionId, { signal } = {}) {
  return request(`/assistant/sessions/${encodeURIComponent(sessionId)}/messages`, {
    method: 'GET',
    headers: getAuthHeaders(),
    signal,
  })
}

/**
 * Translates HTTP or network errors into clear, friendly messages for the customer.
 * @param {Error} error
 * @returns {string}
 */
export function getFriendlyErrorMessage(error) {
  if (!error) return 'An unexpected error occurred. Please try again.'

  if (error.status === 401) {
    return 'Your login session has expired. Please sign in again to continue.'
  }
  if (error.status === 403) {
    return 'You do not have permission to access this chat session.'
  }
  if (error.status === 404) {
    return 'The chat session was not found or has expired.'
  }
  if (error.status === 502) {
    return 'The AI assistant is temporarily unavailable. Please try again shortly.'
  }
  if (error.status === 0 || error.message?.includes('Network error')) {
    return 'Cannot reach SwiftDrop right now. Check your connection and try again.'
  }

  return error.message || 'Something went wrong. Please try again.'
}
