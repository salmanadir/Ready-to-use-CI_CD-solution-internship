// src/components/Navbar/LandingNavbar.jsx
import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { Link } from 'react-router-dom';

const LandingNavbar = () => {
  const { isAuthenticated, user } = useAuth();
  const [activeSection, setActiveSection] = useState('home');
  const [isScrolled, setIsScrolled] = useState(false);

  const handleGitHubLogin = () => {
    window.location.href = 'http://localhost:8080/api/auth/login';
  };

  // Handle scroll effect
  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20);
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // Handle active section detection
  useEffect(() => {
    const handleScroll = () => {
      const sections = ['home', 'features', 'contact'];
      const scrollPosition = window.scrollY + 100;

      for (const section of sections) {
        const element = document.getElementById(section);
        if (element) {
          const offsetTop = element.offsetTop;
          const offsetHeight = element.offsetHeight;
          
          if (scrollPosition >= offsetTop && scrollPosition < offsetTop + offsetHeight) {
            setActiveSection(section);
            break;
          }
        }
      }
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const scrollToSection = (sectionId) => {
    const element = document.getElementById(sectionId);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <nav className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
      isScrolled 
        ? 'bg-gray-900/95 backdrop-blur-lg border-b border-gray-700/50 shadow-lg' 
        : 'bg-gray-900/80 backdrop-blur-sm border-b border-gray-700/30'
    }`}>
      <div className="w-full px-4">
        <div className="flex justify-between items-center h-24">
          {/* Logo - Extreme Left */}
          <div className="flex items-center">
            <div className="w-12 h-12 bg-gradient-to-r from-cyan-400 to-blue-500 rounded-lg flex items-center justify-center mr-4 shadow-lg">
              <span className="text-white font-bold text-xl">DM</span>
            </div>
            <span className="text-3xl font-bold text-white tracking-tight">DeployMate</span>
          </div>

          {/* Navigation Links - Center */}
          <div className="hidden md:flex items-center space-x-2 absolute left-1/2 transform -translate-x-1/2">
            {[
              { id: 'home', label: 'Home' },
              { id: 'features', label: 'Features' },
              { id: 'contact', label: 'Contact' }
            ].map((item) => (
              <button
                key={item.id}
                onClick={() => scrollToSection(item.id)}
                className={`relative px-8 py-3 rounded-lg text-lg font-semibold transition-all duration-200 ${
                  activeSection === item.id
                    ? 'text-cyan-400 bg-cyan-500/10 shadow-sm'
                    : 'text-gray-300 hover:text-white hover:bg-gray-800/50'
                }`}
              >
                {item.label}
                {activeSection === item.id && (
                  <div className="absolute bottom-1 left-1/2 transform -translate-x-1/2 w-2 h-2 bg-cyan-400 rounded-full"></div>
                )}
              </button>
            ))}
          </div>

          {/* Auth Section - Extreme Right */}
          <div className="flex items-center">
            {isAuthenticated ? (
              <div className="flex items-center space-x-6">
                <div className="flex items-center space-x-4">
                  <img 
                    src={user.avatarUrl} 
                    alt={user.username} 
                    className="w-10 h-10 rounded-full border-2 border-cyan-400/30"
                  />
                  <span className="text-gray-200 text-lg font-medium hidden lg:block">Welcome, {user.username}!</span>
                </div>
                <Link 
                  to="/dashboard"
                  className="bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-700 hover:to-blue-700 text-white px-8 py-3 rounded-lg text-lg font-bold transition-all duration-200 transform hover:scale-105 shadow-lg"
                >
                  Dashboard
                </Link>
              </div>
            ) : (
              <button 
                onClick={handleGitHubLogin}
                className="bg-gray-800 hover:bg-gray-700 text-white px-8 py-3 rounded-lg text-lg font-bold border border-gray-600 hover:border-gray-500 transition-all duration-200 flex items-center space-x-3 shadow-lg"
              >
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 0C5.374 0 0 5.373 0 12 0 17.302 3.438 21.8 8.207 23.387c.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0112 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576C20.566 21.797 24 17.3 24 12c0-6.627-5.373-12-12-12z"/>
                </svg>
                <span>Login</span>
              </button>
            )}
          </div>

          {/* Mobile Menu Button */}
          <div className="md:hidden ml-4">
            <button className="text-gray-300 hover:text-white p-2">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6h16M4 12h16M4 18h16"></path>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </nav>
  );
};

export default LandingNavbar;