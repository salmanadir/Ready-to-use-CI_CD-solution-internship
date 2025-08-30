import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';

const History = () => {
  const { user } = useAuth();
  const [historyData, setHistoryData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [isRealData, setIsRealData] = useState(false); // ✅ Track if data is real

  // Fetch history data from backend
  const fetchHistoryData = async () => {
    try {
      setLoading(true);
      const token = localStorage.getItem('token');
      
      if (!token) {
        throw new Error('No authentication token found');
      }

      console.log('🔍 Fetching history data...');

      const response = await fetch('http://localhost:8080/api/history/user-activity', {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      console.log('📡 Response status:', response.status);

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      console.log('📊 Backend response:', data);
      
      if (data.success) {
        console.log('✅ Using REAL data from backend');
        console.log('📋 History items count:', data.history.length);
        
        // ✅ Transform real data
        const transformedHistory = data.history.map(item => ({
          ...item,
          repo: item.repoName,
          timestamp: formatTimestamp(item.createdAt)
        }));
        
        setHistoryData(transformedHistory);
        setIsRealData(true); // ✅ Mark as real data
      } else {
        throw new Error(data.message || 'Failed to fetch history');
      }
      
      setError(null);
    } catch (err) {
      console.error('❌ API Error:', err);
      setError(err.message);
      setHistoryData([]); // ✅ Set empty array instead of mock data
      setIsRealData(false); // ✅ Mark as no real data
    } finally {
      setLoading(false);
    }
  };

  // Format timestamp for display
  const formatTimestamp = (timestamp) => {
    if (!timestamp) return 'Recently';
    
    const date = new Date(timestamp);
    const now = new Date();
    const diffInMinutes = Math.floor((now - date) / (1000 * 60));
    
    if (diffInMinutes < 1) return 'Just now';
    if (diffInMinutes < 60) return `${diffInMinutes} minutes ago`;
    
    const diffInHours = Math.floor(diffInMinutes / 60);
    if (diffInHours < 24) return `${diffInHours} hours ago`;
    
    const diffInDays = Math.floor(diffInHours / 24);
    if (diffInDays < 7) return `${diffInDays} days ago`;
    
    return date.toLocaleDateString();
  };

  // Real-time updates using polling
  useEffect(() => {
    fetchHistoryData();
    const interval = setInterval(fetchHistoryData, 30000);
    return () => clearInterval(interval);
  }, [user]);

  const getStatusColor = (status) => {
    switch (status) {
      case 'success':
      case 'deployed':
        return 'text-green-400 bg-green-500/20';
      case 'failed':
      case 'error':
        return 'text-red-400 bg-red-500/20';
      case 'pending':
      case 'rollback':
        return 'text-yellow-400 bg-yellow-500/20';
      default:
        return 'text-gray-400 bg-gray-500/20';
    }
  };

  const getActionIcon = (type) => {
    switch (type) {
      case 'ci':
        return '⚡';
      case 'cd':
        return '🚀';
      case 'repo':
        return '📁';
      default:
        return '📝';
    }
  };

  const getActionColor = (type) => {
    switch (type) {
      case 'ci':
        return 'text-green-400';
      case 'cd':
        return 'text-purple-400';
      case 'repo':
        return 'text-blue-400';
      default:
        return 'text-gray-400';
    }
  };

  // File viewer modal
  const FileViewerModal = ({ file, onClose }) => (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-gray-800 border border-gray-700 rounded-xl max-w-4xl w-full max-h-[80vh] overflow-hidden">
        <div className="flex items-center justify-between p-4 border-b border-gray-700">
          <h3 className="text-lg font-semibold text-white flex items-center">
            <span className="text-xl mr-2">
              {file.type === 'ci' ? '⚡' : '🚀'}
            </span>
            {file.type === 'ci' ? 'CI' : 'CD'} Workflow - {file.repo}
          </h3>
          <button
            onClick={onClose}
            className="w-8 h-8 bg-gray-700 hover:bg-gray-600 rounded-lg flex items-center justify-center text-gray-400 hover:text-white transition-colors"
          >
            ✕
          </button>
        </div>
        
        <div className="p-4 overflow-auto max-h-[60vh]">
          <pre className="bg-gray-900 text-gray-300 p-4 rounded-lg text-sm overflow-x-auto border border-gray-700">
            <code>{file.workflowContent || 'No content available'}</code>
          </pre>
        </div>
        
        <div className="p-4 border-t border-gray-700 bg-gray-750">
          <div className="flex items-center justify-between text-sm text-gray-400">
            <span>Generated on {file.timestamp}</span>
            <button
              onClick={() => navigator.clipboard.writeText(file.workflowContent)}
              className="bg-cyan-500 hover:bg-cyan-600 text-white px-3 py-1.5 rounded text-xs font-medium transition-colors"
            >
              Copy to Clipboard
            </button>
          </div>
        </div>
      </div>
    </div>
  );

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-bold text-white">Recent Activity</h2>
          <div className="w-8 h-8 bg-green-500/20 rounded-lg flex items-center justify-center">
            <span className="text-lg">📊</span>
          </div>
        </div>
        
        <div className="bg-gray-800/50 backdrop-blur-sm border border-gray-700 rounded-xl p-6">
          <div className="animate-pulse space-y-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="flex items-center space-x-4">
                <div className="w-10 h-10 bg-gray-700 rounded-lg"></div>
                <div className="flex-1 space-y-2">
                  <div className="h-4 bg-gray-700 rounded w-3/4"></div>
                  <div className="h-3 bg-gray-700 rounded w-1/2"></div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-white">Recent Activity</h2>
        <div className="flex items-center space-x-2">
          <button 
            onClick={fetchHistoryData}
            className="w-8 h-8 bg-green-500/20 rounded-lg flex items-center justify-center hover:bg-green-500/30 transition-colors"
            title="Refresh"
          >
            <span className="text-lg">🔄</span>
          </button>
          {error && (
            <div className="w-2 h-2 bg-red-500 rounded-full" title="Connection error"></div>
          )}
          {/* ✅ Debug indicator */}
          {isRealData && (
            <div className="w-2 h-2 bg-green-500 rounded-full" title="Real data"></div>
          )}
        </div>
      </div>

      {/* History Timeline */}
      <div className="bg-gray-800/50 backdrop-blur-sm border border-gray-700 rounded-xl p-6">
        <div className="space-y-4">
          {historyData.length > 0 ? (
            historyData.map((item, index) => (
              <div
                key={item.id}
                className={`flex items-start space-x-4 pb-4 ${
                  index !== historyData.length - 1 ? 'border-b border-gray-700/50' : ''
                }`}
              >
                <div className="w-10 h-10 bg-gray-700/50 rounded-lg flex items-center justify-center flex-shrink-0 mt-1">
                  <span className="text-lg">{getActionIcon(item.type)}</span>
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-1">
                    <h4 className="text-white font-medium text-sm truncate">
                      {item.repo}
                    </h4>
                    <span className="text-gray-500 text-xs flex-shrink-0 ml-2">
                      {item.timestamp}
                    </span>
                  </div>
                  
                  <div className="flex items-center space-x-2 mb-2">
                    <p className={`text-sm ${getActionColor(item.type)}`}>
                      {item.action}
                    </p>
                    {(item.type === 'ci' || item.type === 'cd') && item.workflowContent && (
                      <button
                        onClick={() => setSelectedFile(item)}
                        className="text-cyan-400 hover:text-cyan-300 text-xs underline"
                      >
                        View {item.type.toUpperCase()} File
                      </button>
                    )}
                  </div>
                  
                  <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(item.status)}`}>
                    {(item.status === 'success' || item.status === 'deployed') && '✓'}
                    {(item.status === 'failed' || item.status === 'error') && '✕'}
                    {(item.status === 'pending' || item.status === 'rollback') && '○'}
                    <span className="ml-1 capitalize">{item.status}</span>
                  </span>
                </div>
              </div>
            ))
          ) : (
            // ✅ New empty state for users with no data
            <div className="text-center py-12">
              <div className="w-20 h-20 bg-gray-700/30 rounded-full flex items-center justify-center mx-auto mb-6">
                <span className="text-3xl">🌟</span>
              </div>
              <h3 className="text-xl font-semibold text-white mb-3">No Operations Yet</h3>
              <p className="text-gray-400 mb-2 max-w-sm mx-auto">
                You haven't performed any CI/CD operations yet. Start by connecting your first repository!
              </p>
              <p className="text-gray-500 text-sm mb-6">
                Once you connect a repo and generate workflows, your activity will appear here.
              </p>
              
              {/* ✅ Call-to-action buttons */}
              <div className="flex justify-center space-x-3">
                <button 
                  onClick={() => window.location.href = '/select-repo'}
                  className="bg-gradient-to-r from-cyan-500 to-blue-600 text-white px-4 py-2 rounded-lg text-sm font-semibold hover:from-cyan-600 hover:to-blue-700 transition-all transform hover:scale-105 shadow-lg"
                >
                  Connect Repository
                </button>
                <button 
                  onClick={fetchHistoryData}
                  className="bg-gray-700 hover:bg-gray-600 text-white px-4 py-2 rounded-lg text-sm font-semibold transition-colors"
                >
                  Refresh
                </button>
              </div>
            </div>
          )}
        </div>

        {/* View All Button - only show if there's data */}
        {historyData.length > 0 && (
          <div className="mt-6 pt-4 border-t border-gray-700/50">
            <button className="w-full text-cyan-400 hover:text-cyan-300 text-sm font-medium transition-colors">
              View All Activity →
            </button>
          </div>
        )}
      </div>

      {/* File Viewer Modal */}
      {selectedFile && (
        <FileViewerModal 
          file={selectedFile} 
          onClose={() => setSelectedFile(null)} 
        />
      )}

      {/* Quick Actions - only show if no data */}
      {historyData.length === 0 && (
        <div className="bg-gradient-to-r from-cyan-500/10 to-blue-500/10 border border-cyan-500/20 rounded-xl p-6">
          <h3 className="text-lg font-semibold text-white mb-3 flex items-center">
            <span className="text-xl mr-2">🚀</span>
            Get Started with DeployMate
          </h3>
          <p className="text-gray-400 text-sm mb-4">
            Ready to automate your deployments? Follow these steps to get started:
          </p>
          <div className="space-y-2 text-sm text-gray-300">
            <div className="flex items-center space-x-2">
              <span className="text-cyan-400">1.</span>
              <span>Connect your GitHub repository</span>
            </div>
            <div className="flex items-center space-x-2">
              <span className="text-cyan-400">2.</span>
              <span>Generate CI workflow for automated testing</span>
            </div>
            <div className="flex items-center space-x-2">
              <span className="text-cyan-400">3.</span>
              <span>Set up CD pipeline for automatic deployments</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default History;