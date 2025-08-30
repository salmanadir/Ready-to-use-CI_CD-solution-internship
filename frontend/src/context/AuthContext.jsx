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

    return {
      get: async (url) => {
        try {
          const response = await fetch(`http://localhost:8080${url}`, {
            headers: {
              'Authorization': `Bearer ${token}`,
              'Content-Type': 'application/json',
            },
          });
          
          if (response.status === 401) {
            logout();
            return null;
          }
          
          return await response.json();
        } catch (error) {
          console.error('API GET error:', error);
          return null;
        }
      },

      post: async (url, data) => {
        try {
          const response = await fetch(`http://localhost:8080${url}`, {
            method: 'POST',
            headers: {
              'Authorization': `Bearer ${token}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(data),
          });
          
          if (response.status === 401) {
            logout();
            return null;
          }
          
          return await response.json();
        } catch (error) {
          console.error('API POST error:', error);
          return null;
        }
      },

      put: async (url, data) => {
        try {
          const response = await fetch(`http://localhost:8080${url}`, {
            method: 'PUT',
            headers: {
              'Authorization': `Bearer ${token}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(data),
          });
          
          if (response.status === 401) {
            logout();
            return null;
          }
          
          return await response.json();
        } catch (error) {
          console.error('API PUT error:', error);
          return null;
        }
      },

      delete: async (url) => {
        try {
          const response = await fetch(`http://localhost:8080${url}`, {
            method: 'DELETE',
            headers: {
              'Authorization': `Bearer ${token}`,
              'Content-Type': 'application/json',
            },
          });
          
          if (response.status === 401) {
            logout();
            return null;
          }
          
          return response.ok;
        } catch (error) {
          console.error('API DELETE error:', error);
          return false;
        }
      }
    };
  }, [token, logout]); // Only recreate when token changes
    // In AuthContext.jsx - add this new function before the return statement:

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
      const error = await response.json();
      console.error('Delete account failed:', error.error);
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