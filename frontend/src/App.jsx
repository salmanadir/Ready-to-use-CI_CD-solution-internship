import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import LandingPage from './pages/LandingPage/LandingPage';
import Dashboard from './pages/Dashboard/Dashboard';
import CDGeneration from './pages/Dashboard/cdGeneration';
import AuthCallback from './pages/AuthCallback/Authcallback';
import ProtectedRoute from './components/ProtectedRoute';
import RepoAnalysisPage from './pages/RepoAnalysis/RepoAnalysisPage';
import RepoSelectionPage from './pages/RepoSelection/RepoSelectionPage';
import "@fortawesome/fontawesome-free/css/all.min.css";
import './App.css'; 


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
          <Route  
          path="/dashboard/select-repo"  
          element={  
              <ProtectedRoute>  
                <RepoSelectionPage />  
              </ProtectedRoute>  
         }  
          />
          <Route   
          path="/analysis/:repoId"   
          element={  
              <ProtectedRoute>  
                <RepoAnalysisPage />  
              </ProtectedRoute>  
        }   
          />
          <Route
            path="/dashboard/cd-generation"
            element={
              <ProtectedRoute>
                <CDGeneration />
              </ProtectedRoute>
            }
          />
        </Routes>
      </Router>
    </AuthProvider>
  );
}
export default App;