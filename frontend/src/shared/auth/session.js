/**
 * The signed-in session (JWT, refresh token and user profile), kept in localStorage so it
 * survives a page reload. AuthContext and the API client both read and write it through
 * here, and AuthContext listens for changes (for example when a refresh fails and the
 * API client signs the user out).
 */
const KEYS = {
  accessToken: 'swiftdrop_token',
  refreshToken: 'swiftdrop_refresh_token',
  user: 'swiftdrop_user',
}

const listeners = new Set()

function read(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key, value) {
  try {
    if (value == null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    // Storage blocked (private mode): the session lasts until the tab closes.
  }
}

export function getAccessToken() {
  return read(KEYS.accessToken)
}

export function getRefreshToken() {
  return read(KEYS.refreshToken)
}

export function getStoredUser() {
  try {
    return JSON.parse(read(KEYS.user) || 'null')
  } catch {
    return null
  }
}

function notify() {
  const session = { accessToken: getAccessToken(), user: getStoredUser() }
  listeners.forEach((listener) => listener(session))
}

/** Saves an AuthResponse from login, signup, Google sign-in, email verification or refresh. */
export function saveSession({ accessToken, refreshToken, user }) {
  write(KEYS.accessToken, accessToken)
  if (refreshToken) write(KEYS.refreshToken, refreshToken)
  if (user) write(KEYS.user, JSON.stringify(user))
  notify()
}

export function saveUser(user) {
  write(KEYS.user, JSON.stringify(user))
  notify()
}

export function clearSession() {
  Object.values(KEYS).forEach((key) => write(key, null))
  notify()
}

/** @returns {() => void} unsubscribe */
export function onSessionChange(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Where a user goes after signing in: customers to their dashboard, everyone else to their profile. */
export function homePathFor(user) {
  return user?.role === 'ROLE_CUSTOMER' ? '/app' : '/profile'
}
