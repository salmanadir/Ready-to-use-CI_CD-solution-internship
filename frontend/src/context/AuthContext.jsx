// src/context/AuthContext.jsx
import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';

const AuthContext = createContext();

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  // Load auth state from localStorage on app start
  useEffect(() => {
    const savedToken = localStorage.getItem('auth_token');
    const savedUser = localStorage.getItem('user_data');
    
    if (savedToken && savedUser) {
      try {
        setToken(savedToken);
        setUser(JSON.parse(savedUser));
        setIsAuthenticated(true);
      } catch (error) {
        console.error('Error loading saved auth data:', error);
        localStorage.removeItem('auth_token');
        localStorage.removeItem('user_data');
      }
    }
    setIsLoading(false);
  }, []); // Empty dependency array - only run once

  // Login function (called by AuthCallback)
  const login = useCallback((jwtToken, userData) => {
    localStorage.setItem('auth_token', jwtToken);
    localStorage.setItem('user_data', JSON.stringify(userData));
    setToken(jwtToken);
    setUser(userData);
    setIsAuthenticated(true);
  }, []);

  // Logout function
  const logout = useCallback(() => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('user_data');
    setToken(null);
    setUser(null);
    setIsAuthenticated(false);
  }, []);

  // Memoize API client to prevent recreation on every render
  const apiClient = useMemo(() => {
    if (!token) return null;

    // --- petit parseur robuste : JSON si possible, sinon texte ---
    const parseSmart = async (response) => {
      const ct = response.headers.get('content-type') || '';
      if (ct.includes('application/json')) {
        try { return await response.json(); } catch { /* fallback below */ }
      }
      try { return await response.text(); } catch { return null; }
    };

    // --- couche commune GET/POST/PUT/DELETE ---
    const base = async (method, url, data) => {
      const res = await fetch(`http://localhost:8080${url}`, {
        method,
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: data !== undefined ? JSON.stringify(data) : undefined,
      });

      const payload = await parseSmart(res);

      // 401 → logout + erreur structurée
      if (res.status === 401) {
        logout();
        throw { status: 401, payload: { message: 'Unauthorized' } };
      }

      // autres statuts non OK → erreur structurée (lisible par humanize*Error)
      if (!res.ok) {
        const message =
          typeof payload === 'string'
            ? payload.slice(0, 400)
            : (payload?.message || 'Request failed');
        throw { status: res.status, payload: { message } };
      }

      // OK → renvoie le JSON (ou texte si non-JSON)
      return payload;
    };

    return {
      get: async (url) => {
        try {
          return await base('GET', url);
        } catch (error) {
          console.error('API GET error:', error);
          throw error;
        }
      },

      post: async (url, data) => {
        try {
          return await base('POST', url, data);
        } catch (error) {
          console.error('API POST error:', error);
          throw error;
        }
      },

      put: async (url, data) => {
        try {
          return await base('PUT', url, data);
        } catch (error) {
          console.error('API PUT error:', error);
          throw error;
        }
      },

      delete: async (url) => {
        try {
          await base('DELETE', url);
          return true;
        } catch (error) {
          console.error('API DELETE error:', error);
          throw error;
        }
      }
    };
  }, [token, logout]); // Only recreate when token changes

  // Delete account function
  const deleteAccount = useCallback(async () => {
    if (!token) {
      console.error('No authentication token available');
      return false;
    }

    try {
      const response = await fetch('http://localhost:8080/api/auth/delete-account', {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        const result = await response.json();
        console.log('Account deleted:', result.message);
        
        // Clear all data after successful deletion
        localStorage.removeItem('auth_token');
        localStorage.removeItem('user_data');
        sessionStorage.clear();
        
        // Reset state
        setToken(null);
        setUser(null);
        setIsAuthenticated(false);
        setIsLoading(false);
        
        return true;
      } else {
        // essaie de lire l'erreur proprement
        let errMsg = 'Delete account failed';
        try {
          const error = await response.json();
          errMsg = error?.error || error?.message || errMsg;
        } catch {/* ignore */}
        console.error('Delete account failed:', errMsg);
        return false;
      }
    } catch (error) {
      console.error('Delete account error:', error);
      return false;
    }
  }, [token]);

  // Memoize the context value
  const value = useMemo(() => ({
    user,
    isAuthenticated,
    isLoading,
    apiClient,
    logout,
    login,
    token,
    deleteAccount,
  }), [user, isAuthenticated, isLoading, apiClient, logout, login, token, deleteAccount]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};
