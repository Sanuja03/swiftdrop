import { request } from '@/shared/api/httpClient';

/**
 * Auth module API calls. They all go through the shared HTTP client (base URL from
 * VITE_API_BASE_URL, JWT attached, refresh on 401) and keep their original return shapes:
 * { success: true, data } or { success: false, error }.
 *
 * Sign-in style calls pass redirectOnUnauthorized: false, because there a 401 means
 * "wrong credentials or code", not "session expired".
 */
const PUBLIC_CALL = { redirectOnUnauthorized: false };

async function attempt(call, fallbackError) {
  try {
    return { success: true, data: await call() };
  } catch (error) {
    const offline = error.status === 0;
    return {
      success: false,
      error: offline ? 'Unable to connect to SwiftDrop authentication server.' : error.message || fallbackError,
    };
  }
}

export function loginUser(email, password) {
  return attempt(
    () => request('/auth/login', { json: { email, password }, ...PUBLIC_CALL }),
    'Login failed. Please check your credentials.',
  );
}

export function signupUser(userData) {
  return attempt(() => request('/auth/signup', { json: userData, ...PUBLIC_CALL }), 'Registration failed.');
}

export function loginWithGoogle(idToken) {
  return attempt(
    () => request('/auth/google', { json: { idToken }, ...PUBLIC_CALL }),
    'Google authentication failed.',
  );
}

/** The signed-in user's profile, or null. The token argument is kept for compatibility; the client sends the JWT. */
// eslint-disable-next-line no-unused-vars
export async function getCurrentUser(token) {
  try {
    return await request('/auth/me');
  } catch {
    return null;
  }
}

// eslint-disable-next-line no-unused-vars
export function updateUserProfile(profileData, token) {
  return attempt(
    () => request('/auth/profile', { method: 'PUT', json: profileData }),
    'Failed to update profile details.',
  );
}

/** Revokes the user's refresh tokens on the server. Never throws. */
export async function logoutUser() {
  try {
    await request('/auth/logout', { method: 'POST', ...PUBLIC_CALL });
  } catch {
    // Signing out locally still works if the server cannot be reached.
  }
}

export function requestPasswordReset(email) {
  return attempt(
    () => request('/auth/forgot-password', { json: { email }, ...PUBLIC_CALL }),
    'Failed to request password reset code.',
  );
}

export function resetUserPassword(token, newPassword) {
  return attempt(
    () => request('/auth/reset-password', { json: { token, newPassword }, ...PUBLIC_CALL }),
    'Failed to reset password.',
  );
}

export function verifyEmailCode(email, code) {
  return attempt(
    () => request('/auth/verify-email', { json: { email, code }, ...PUBLIC_CALL }),
    'Email verification failed. Please check your code.',
  );
}

export function resendVerificationEmail(email) {
  return attempt(
    () => request('/auth/resend-verification', { json: { email }, ...PUBLIC_CALL }),
    'Failed to resend verification code.',
  );
}

/** Role test helper used by the profile page: reports the status instead of throwing. */
// eslint-disable-next-line no-unused-vars
export async function testEndpoint(endpoint, token) {
  try {
    const data = await request(endpoint, PUBLIC_CALL);
    return { status: 200, ok: true, data };
  } catch (error) {
    return { status: error.status || 500, ok: false, data: error.body, error: error.message };
  }
}
