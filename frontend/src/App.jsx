// src/App.jsx
import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import LandingPage from './pages/LandingPage/LandingPageTemp';
import Dashboard from './pages/Dashboard/Dashboard';
import AuthCallback from './pages/AuthCallback/Authcallback';
import ProtectedRoute from './components/ProtectedRoute';
import './styles/globals.css'; // Global styles



function App() {
  return (
    <AuthProvider>
      <Router>
        <Routes>
          {/* Public routes */}
          <Route path="/" element={<LandingPage />} />
          <Route path="/auth/callback" element={<AuthCallback />} />
          <Route path="/LandingTest" element={<LandingPage />} />
        
          {/* Protected routes for your team */}
          <Route 
            path="/dashboard" 
            element={
              <ProtectedRoute>
                <Dashboard />
              </ProtectedRoute>
            } 
          />
          

          {/* Add more routes for your team as needed */}
        </Routes>
      </Router>
    </AuthProvider>
  );
}

export default App;