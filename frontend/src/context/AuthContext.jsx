import React, { createContext, useContext, useState, useEffect } from 'react';
import { loginUser, loginWithGoogle as apiLoginWithGoogle, getCurrentUser } from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const savedUser = localStorage.getItem('swiftdrop_user');
    return savedUser ? JSON.parse(savedUser) : null;
  });
  const [token, setToken] = useState(() => localStorage.getItem('swiftdrop_token'));
  const [loading, setLoading] = useState(false);

  const login = async (email, password) => {
    setLoading(true);
    const result = await loginUser(email, password);
    setLoading(false);

    if (result.success && result.data) {
      const { accessToken, user: profile } = result.data;
      setToken(accessToken);
      setUser(profile);
      localStorage.setItem('swiftdrop_token', accessToken);
      localStorage.setItem('swiftdrop_user', JSON.stringify(profile));
      return { success: true, user: profile };
    }

    return { success: false, error: result.error };
  };

  const loginWithGoogle = async (idToken) => {
    setLoading(true);
    const result = await apiLoginWithGoogle(idToken);
    setLoading(false);

    if (result.success && result.data) {
      const { accessToken, user: profile } = result.data;
      setToken(accessToken);
      setUser(profile);
      localStorage.setItem('swiftdrop_token', accessToken);
      localStorage.setItem('swiftdrop_user', JSON.stringify(profile));
      return { success: true, user: profile };
    }

    return { success: false, error: result.error };
  };

  const logout = () => {
    setUser(null);
    setToken(null);
    localStorage.removeItem('swiftdrop_token');
    localStorage.removeItem('swiftdrop_user');
  };

  return (
    <AuthContext.Provider value={{ user, token, isAuthenticated: !!token, loading, login, loginWithGoogle, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}

