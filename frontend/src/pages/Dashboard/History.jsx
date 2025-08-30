import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';

const History = () => {
  const { token } = useAuth();
  const [historyData, setHistoryData] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);

  // Fetch history data
  const fetchHistory = async () => {
    if (!token) {
      console.log('🔑 No token available');
      setIsLoading(false);
      return;
    }

    try {
      console.log('🔍 Fetching history data...');
      console.log('🔑 Token exists:', !!token);

      const response = await fetch('http://localhost:8080/api/history/user-activity', {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        credentials: 'include',
      });

      console.log('📡 Response status:', response.status);
      console.log('📡 Response ok:', response.ok);

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      console.log('📊 Backend response:', data);

      if (data.success) {
        console.log('✅ Using REAL data from backend');
        console.log('📋 History items count:', data.history?.length || 0);
        setHistoryData(data.history || []);
        setError(null);
      } else {
        throw new Error(data.message || 'Failed to fetch history');
      }
    } catch (error) {
      console.error('❌ Error fetching history:', error);
      setError(error.message);
      setHistoryData([]); // Show empty state instead of crashing
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory();
    
    // ✅ Auto-refresh every 30 seconds
    const interval = setInterval(fetchHistory, 30000);
    return () => clearInterval(interval);
  }, [token]);

  // Format timestamp
  const formatTimestamp = (timestamp) => {
    if (!timestamp) return 'Unknown time';
    
    try {
      const date = new Date(timestamp);
      const now = new Date();
      const diffInMinutes = Math.floor((now - date) / (1000 * 60));
      
      if (diffInMinutes < 1) return 'Just now';
      if (diffInMinutes < 60) return `${diffInMinutes} minute${diffInMinutes !== 1 ? 's' : ''} ago`;
      
      const diffInHours = Math.floor(diffInMinutes / 60);
      if (diffInHours < 24) return `${diffInHours} hour${diffInHours !== 1 ? 's' : ''} ago`;
      
      const diffInDays = Math.floor(diffInHours / 24);
      return `${diffInDays} day${diffInDays !== 1 ? 's' : ''} ago`;
    } catch (error) {
      return 'Unknown time';
    }
  };

  // Get status color classes
  const getStatusColor = (status) => {
    switch (status?.toLowerCase()) {
      case 'success':
        return 'bg-green-500/20 text-green-400 border-green-500/30';
      case 'failed':
      case 'error':
        return 'bg-red-500/20 text-red-400 border-red-500/30';
      case 'pending':
      case 'running':
        return 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30';
      default:
        return 'bg-gray-500/20 text-gray-400 border-gray-500/30';
    }
  };

  // Get operation icon
  const getOperationIcon = (type) => {
    switch (type) {
      case 'connection':
        return '🔗';
      case 'ci':
        return '⚡';
      case 'cd':
        return '🚀';
      default:
        return '📝';
    }
  };

  // Loading state
  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-bold text-white">Recent Activity</h2>
          <div className="flex items-center space-x-2">
            <div className="w-2 h-2 bg-blue-500 rounded-full animate-pulse"></div>
            <span className="text-sm text-gray-400">Loading...</span>
          </div>
        </div>

        <div className="space-y-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="bg-gray-750 border border-gray-600 rounded-lg p-4 animate-pulse">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 bg-gray-600 rounded-lg"></div>
                <div className="flex-1 space-y-2">
                  <div className="h-4 bg-gray-600 rounded w-1/3"></div>
                  <div className="h-3 bg-gray-700 rounded w-1/2"></div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-white">Recent Activity</h2>
        <div className="flex items-center space-x-2">
          <div className={`w-2 h-2 rounded-full ${error ? 'bg-red-500' : 'bg-green-500'}`}></div>
          <span className="text-sm text-gray-400">
            {error ? 'Connection Error' : 'Live Updates'}
          </span>
        </div>
      </div>

      {/* Error State */}
      {error && (
        <div className="bg-red-500/10 border border-red-500/30 rounded-lg p-4">
          <div className="flex items-center space-x-3">
            <span className="text-red-400 text-xl">⚠️</span>
            <div>
              <h3 className="text-red-400 font-medium">Connection Error</h3>
              <p className="text-gray-400 text-sm">{error}</p>
            </div>
          </div>
        </div>
      )}

      {/* ✅ Updated History Display - Grouped by Repository */}
      <div className="space-y-4">
        {historyData.length > 0 ? (
          historyData.map((repoItem, index) => (
            <div
              key={repoItem.id}
              className="bg-gray-750 border border-gray-600 rounded-lg p-4"
            >
              {/* ✅ Repository Header */}
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center space-x-3">
                  <div className="w-10 h-10 bg-blue-500/20 rounded-lg flex items-center justify-center">
                    <span className="text-lg">📁</span>
                  </div>
                  <div>
                    <h4 className="text-white font-semibold text-base">
                      {repoItem.repoName}
                    </h4>
                    <p className="text-gray-400 text-sm">
                      {repoItem.action} • {repoItem.operationCount} operation{repoItem.operationCount !== 1 ? 's' : ''}
                    </p>
                  </div>
                </div>
                <span className="text-gray-500 text-xs">
                  {formatTimestamp(repoItem.lastActivity)}
                </span>
              </div>

              {/* ✅ Operations List */}
              <div className="space-y-2 ml-13">
                {repoItem.operations?.map((operation, opIndex) => (
                  <div
                    key={opIndex}
                    className="flex items-center justify-between py-2 px-3 bg-gray-800/50 rounded-lg"
                  >
                    <div className="flex items-center space-x-3">
                      <span className="text-lg">
                        {getOperationIcon(operation.type)}
                      </span>
                      <div>
                        <p className="text-gray-200 text-sm">{operation.action}</p>
                        <p className="text-gray-500 text-xs">
                          {formatTimestamp(operation.timestamp)}
                        </p>
                      </div>
                    </div>
                    
                    <div className="flex items-center space-x-2">
                      <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium border ${getStatusColor(operation.status)}`}>
                        {operation.status === 'success' && '✓'}
                        {operation.status === 'failed' && '✕'}
                        {operation.status === 'pending' && '○'}
                        <span className="ml-1 capitalize">{operation.status}</span>
                      </span>
                      
                      {/* ✅ View File Button - Only for CI/CD */}
                      {(operation.type === 'ci' || operation.type === 'cd') && operation.workflowContent && (
                        <button
                          onClick={() => setSelectedFile({
                            ...operation,
                            repo: repoItem.repoName
                          })}
                          className="text-cyan-400 hover:text-cyan-300 text-xs underline transition-colors"
                        >
                          View {operation.type.toUpperCase()}
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))
        ) : (
          // ✅ Empty State
          <div className="text-center py-12">
            <div className="w-16 h-16 bg-gray-700 rounded-full flex items-center justify-center mx-auto mb-4">
              <span className="text-2xl">📋</span>
            </div>
            <h3 className="text-xl font-semibold text-white mb-2">No Operations Yet</h3>
            <p className="text-gray-400 mb-6 max-w-md mx-auto">
              Start by connecting a repository to see your CI/CD workflow history here.
            </p>
            <div className="flex flex-col sm:flex-row gap-3 justify-center">
              <button className="px-6 py-2 bg-cyan-600 hover:bg-cyan-700 text-white rounded-lg transition-colors">
                Connect Repository
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ✅ File Viewer Modal */}
      {selectedFile && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-gray-800 border border-gray-700 rounded-xl max-w-4xl w-full max-h-[80vh] overflow-hidden">
            {/* Modal Header */}
            <div className="flex items-center justify-between p-4 border-b border-gray-700">
              <h3 className="text-lg font-semibold text-white">
                {selectedFile.type?.toUpperCase()} Workflow - {selectedFile.repo}
              </h3>
              <button
                onClick={() => setSelectedFile(null)}
                className="text-gray-400 hover:text-white text-xl"
              >
                ✕
              </button>
            </div>

            {/* File Content */}
            <div className="p-4 overflow-auto max-h-[60vh]">
              <pre className="bg-gray-900 border border-gray-600 rounded-lg p-4 text-gray-300 text-sm overflow-auto whitespace-pre-wrap">
                {selectedFile.workflowContent}
              </pre>
            </div>

            {/* Modal Footer */}
            <div className="p-4 border-t border-gray-700 flex justify-end space-x-3">
              <button
                onClick={() => {
                  navigator.clipboard.writeText(selectedFile.workflowContent);
                  // You could add a toast notification here
                }}
                className="px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors"
              >
                Copy to Clipboard
              </button>
              <button
                onClick={() => setSelectedFile(null)}
                className="px-4 py-2 bg-cyan-600 hover:bg-cyan-700 text-white rounded-lg transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default History;