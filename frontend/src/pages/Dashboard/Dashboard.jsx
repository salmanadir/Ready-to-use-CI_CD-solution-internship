import React from 'react';
import { useAuth } from '../../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import History from './History';

const Dashboard = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  // Available features
  const features = [
    {
      id: 'select-repo',
      title: 'Select Repository',
      description: 'Connect and configure your GitHub repositories for deployment',
      icon: '📁',
      color: 'from-blue-500 to-blue-600',
      path: '/dashboard/select-repo'
    },
    {
      id: 'generate-ci',
      title: 'Generate CI',
      description: 'Create Continuous Integration workflows for automated testing',
      icon: '⚡',
      color: 'from-green-500 to-green-600',
      path: '/generate-ci'
    },
    {
      id: 'generate-cd',
      title: 'Generate CD',
      description: 'Set up Continuous Deployment pipelines for automatic releases',
      icon: '🚀',
      color: 'from-purple-500 to-purple-600',
      path: '/generate-cd'
    }
  ];

  return (
    <div className="min-h-screen bg-gray-900">
      {/* Main Content */}
      <div className="px-6 py-8">
        <div className="max-w-7xl mx-auto">
          {/* Welcome Header */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-white mb-2">
              Welcome back, {user?.username}! 👋
            </h1>
            <p className="text-gray-400">
              Manage your CI/CD pipelines and track your deployment history.
            </p>
          </div>

          {/* Main Grid - Two Columns */}
          <div className="grid lg:grid-cols-2 gap-8">
            
            {/* LEFT SECTION - FEATURES */}
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <h2 className="text-2xl font-bold text-white">Available Features</h2>
                <div className="w-8 h-8 bg-cyan-500/20 rounded-lg flex items-center justify-center">
                  <span className="text-lg">🛠️</span>
                </div>
              </div>

              <div className="space-y-4">
                {features.map((feature) => (
                  <div
                    key={feature.id}
                    className="bg-gray-800/50 backdrop-blur-sm border border-gray-700 rounded-xl p-6 hover:border-cyan-500/50 hover:bg-gray-800/70 transition-all group cursor-pointer"
                    onClick={() => navigate(feature.path)}
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex items-start space-x-4">
                        <div className="w-12 h-12 bg-gray-700/50 rounded-lg flex items-center justify-center group-hover:bg-gray-600/50 transition-all">
                          <span className="text-2xl">{feature.icon}</span>
                        </div>
                        <div className="flex-1">
                          <h3 className="text-lg font-semibold text-white mb-2 group-hover:text-cyan-400 transition-colors">
                            {feature.title}
                          </h3>
                          <p className="text-gray-400 text-sm leading-relaxed">
                            {feature.description}
                          </p>
                        </div>
                      </div>
                      <button
                        className={`bg-gradient-to-r ${feature.color} text-white px-4 py-2 rounded-lg text-sm font-semibold hover:shadow-lg transition-all transform hover:scale-105 opacity-80 group-hover:opacity-100`}
                      >
                        Start
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* RIGHT SECTION - HISTORY COMPONENT */}
            <History />
          </div>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;