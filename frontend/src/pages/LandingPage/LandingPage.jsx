// src/pages/LandingPage/LandingPage.jsx
import React from 'react';
import { useAuth } from '../../context/AuthContext';
import { Link } from 'react-router-dom'; // Add this import
import './LandingPage.css'; // Import CSS for styling

const LandingPage = () => {
  const { isAuthenticated, user } = useAuth(); // Remove auto-redirect logic

  const handleGitHubLogin = () => {
    window.location.href = 'http://localhost:8080/api/auth/login';
  };

  return (
    <div className="landing-page">
      <div className="hero">
        <div className="container">
          <h1>Ready-to-use CI/CD Solution</h1>
          <p className="hero-subtitle">
            Automated CI/CD pipelines for your projects with just a few clicks
          </p>
          
          {/* Show different content based on auth status */}
          {isAuthenticated ? (
            <div className="authenticated-section">
              <p className="welcome-back">
                Welcome back, <strong>{user.username}</strong>! 👋
              </p>
              <div className="action-buttons">
                <Link to="/dashboard" className="btn btn-primary">
                  Go to Dashboard
                </Link>
                <button onClick={() => window.location.reload()} className="btn btn-secondary">
                  Refresh Projects
                </button>
              </div>
            </div>
          ) : (
            <div className="login-section">
              <button onClick={handleGitHubLogin} className="github-login-btn">
                <svg className="github-icon" viewBox="0 0 24 24">
                  <path d="M12 0C5.374 0 0 5.373 0 12 0 17.302 3.438 21.8 8.207 23.387c.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0112 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576C20.566 21.797 24 17.3 24 12c0-6.627-5.373-12-12-12z"/>
                </svg>
                Login with GitHub
              </button>
              
              <p className="login-description">
                Connect your GitHub account to analyze repositories and generate CI/CD pipelines
              </p>
            </div>
          )}
        </div>
      </div>

      <section className="features">
        <div className="container">
          <h2>What you can do with our platform</h2>
          
          <div className="features-grid">
            <div className="feature-card">
              <h3>🔍 Repository Analysis</h3>
              <p>Automatically detect your tech stack and dependencies</p>
            </div>
            
            <div className="feature-card">
              <h3>🚀 CI/CD Generation</h3>
              <p>Generate optimized GitHub Actions workflows for your projects</p>
            </div>
            
            <div className="feature-card">
              <h3>🐳 Containerization</h3>
              <p>Create Docker configurations and deployment strategies</p>
            </div>
            
            <div className="feature-card">
              <h3>📊 Stack Detection</h3>
              <p>Intelligent analysis of your project structure and technologies</p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};

export default LandingPage;