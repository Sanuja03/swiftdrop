import { createContext, useContext, useEffect, useState } from 'react';
import { loginUser, loginWithGoogle as apiLoginWithGoogle, logoutUser } from '../services/api';
import { clearSession, getAccessToken, getStoredUser, onSessionChange, saveSession, saveUser } from '@/shared/auth/session';

const AuthContext = createContext(null);

/**
 * Signed-in state for the whole app. The session itself lives in shared/auth/session.js,
 * which the API client also updates (token refresh, sign-out after a failed refresh).
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(getStoredUser);
  const [token, setToken] = useState(getAccessToken);
  const [loading, setLoading] = useState(false);

  useEffect(
    () =>
      onSessionChange(({ accessToken, user: storedUser }) => {
        setToken(accessToken);
        setUser(storedUser);
      }),
    [],
  );

  const signIn = async (call) => {
    setLoading(true);
    const result = await call();
    setLoading(false);

    if (result.success && result.data) {
      saveSession(result.data);
      return { success: true, user: result.data.user };
    }
    return { success: false, error: result.error };
  };

  const login = (email, password) => signIn(() => loginUser(email, password));

  const loginWithGoogle = (idToken) => signIn(() => apiLoginWithGoogle(idToken));

  const logout = async () => {
    await logoutUser();
    clearSession();
  };

  const updateUserSession = (updatedProfile) => {
    saveUser(updatedProfile);
  };

  const setSession = (authData) => {
    if (authData && authData.accessToken) {
      saveSession(authData);
    }
  };

  return (
    <AuthContext.Provider value={{ user, token, isAuthenticated: !!token, loading, login, loginWithGoogle, logout, updateUserSession, setSession }}>
      {children}
    </AuthContext.Provider>
  );
}

// The hook belongs next to its provider; splitting it out would change every import of useAuth.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext);
}
