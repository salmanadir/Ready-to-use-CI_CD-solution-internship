// src/pages/Authcallback.jsx
import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const AuthCallback = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { login } = useAuth();
  const [hasProcessed, setHasProcessed] = useState(false);

  useEffect(() => {
    // Prevent multiple executions
    if (hasProcessed) return;
    
    const handleCallback = () => {
      try {
        const token = searchParams.get('token');
        const userStr = searchParams.get('user');
        const error = searchParams.get('error');

        if (error) {
          console.error('Auth error:', error);
          alert('Authentication failed: ' + error);
          navigate('/', { replace: true });
          return;
        }

        if (token && userStr) {
          const user = JSON.parse(decodeURIComponent(userStr));
          login(token, user);
          setHasProcessed(true);
          
          // Use setTimeout to ensure state update completes
          setTimeout(() => {
            navigate('/dashboard', { replace: true });
          }, 100);
        } else {
          console.error('Missing token or user data');
          navigate('/', { replace: true });
        }
      } catch (error) {
        console.error('Callback processing error:', error);
        alert('Authentication processing failed');
        navigate('/', { replace: true });
      }
    };

    handleCallback();
  }, [searchParams, login, navigate, hasProcessed]);

  return (
    <div className="auth-loading">
      <div className="spinner"></div>
      <p>Completing authentication...</p>
    </div>
  );
};

export default AuthCallback;