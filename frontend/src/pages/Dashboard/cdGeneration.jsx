
import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';

const CDGeneration = () => {
  const { apiClient, isAuthenticated, user, token } = useAuth();
  const [repoId, setRepoId] = useState('');
  const [repos, setRepos] = useState([]);
  const [reposLoading, setReposLoading] = useState(true);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [composeStatus, setComposeStatus] = useState(null);
  const [showComposePreview, setShowComposePreview] = useState(false);
  const [originalAction, setOriginalAction] = useState(null); // 'preview' or 'push'



  // Fetch all user repositories on mount
  useEffect(() => {
    const fetchRepos = async () => {
      setReposLoading(true);
      try {
        const response = await apiClient.get('/api/repositories/selected');
        if (response && response.repositories && response.repositories.length > 0) {
          setRepos(response.repositories);
          setRepoId(response.repositories[0].repoId); // Default to first repo
        } else {
          setRepos([]);
        }
      } catch (e) {
        setRepos([]);
      }
      setReposLoading(false);
    };
    fetchRepos();
    // eslint-disable-next-line
  }, []);

  const handlePreview = async () => {
    if (!repoId) {
      setResult('Please select a repository.');
      return;
    }
    setLoading(true);
    setResult(null);
    setComposeStatus(null);
    setOriginalAction('preview');
    
    try {
      const response = await apiClient.post(`/api/cd-workflow/preview?repoId=${repoId}`);
      let data;
      if (response && typeof response.json === 'function') {
        data = await response.json();
      } else if (response && response.data) {
        data = response.data;
      } else {
        data = response;
      }
      console.log('Preview response:', data);
      
      // Check if Docker Compose is missing
      if (data.missingCompose) {
        setComposeStatus(data);
        await checkComposePreview();
      } else {
        setResult(data);
      }
    } catch (error) {
      console.log('Error caught:', error);
      
      // Check different possible status locations
      const status = error?.status || error?.response?.status;
      const message = error?.payload?.message || error?.response?.data?.message || error?.message;
      
      console.log('Status code:', status);
      console.log('Error message:', message);
      
      // Handle 428 status code (Precondition Required) for missing Docker Compose
      if (status === 428) {
        console.log('Docker Compose missing - 428 status detected, showing modal');
        const composeData = {
          success: false,
          missingCompose: true,
          message: 'Docker Compose is missing. Do you want to generate one?'
        };
        setComposeStatus(composeData);
        setLoading(false); // Reset loading state so modal buttons work
        // Automatically fetch the preview
        setTimeout(() => checkComposePreview(), 100);
        return; // Don't set error result
      } else {
        console.log('Not 428 status, setting error result');
        setResult({ success: false, message: message || 'Preview failed' });
      }
    }
    setLoading(false);
  };

  const checkComposePreview = async () => {
    console.log('checkComposePreview called');
    try {
      const response = await apiClient.post(`/api/workflows/compose/prod/preview`, { repoId: repoId });
      let data;
      if (response && typeof response.json === 'function') {
        data = await response.json();
      } else if (response && response.data) {
        data = response.data;
      } else {
        data = response;
      }
      console.log('Compose preview response:', data);
      setComposeStatus(prev => ({ ...prev, composePreview: data }));
    } catch (error) {
      console.error('Failed to get compose preview:', error);
      console.error('Preview error details:', error?.response?.data);
      // Show the push button anyway, but with an error message
      setComposeStatus(prev => ({ 
        ...prev, 
        error: error?.response?.data?.message || error.message,
        // Allow push even if preview fails
        composePreview: { 
          hasCompose: false,
          composePreview: 'Preview failed to load, but you can still generate the Docker Compose file.',
          message: 'Preview generation failed'
        }
      }));
    }
  };

  const handleGenerateCompose = async () => {
    console.log('handleGenerateCompose started!');
    console.log('repoId:', repoId);
    console.log('originalAction:', originalAction);
    console.log('Current URL before API call:', window.location.href);
    console.log('Auth state before API call:', { isAuthenticated, user: user?.username, hasToken: !!token });
    
    setLoading(true);
    try {
      console.log('Making API call to:', `/api/workflows/compose/prod/apply`);
      console.log('Request body:', { repoId });
      const response = await apiClient.post(`/api/workflows/compose/prod/apply`, { repoId });
      let data;
      if (response && typeof response.json === 'function') {
        data = await response.json();
      } else if (response && response.data) {
        data = response.data;
      } else {
        data = response;
      }
      
      console.log('API response received:', data);
      console.log('Current URL after API call:', window.location.href);
      
      if (data.success) {
        console.log('Docker Compose generated successfully');
        setComposeStatus(null);
        // Show success message briefly
        setResult({ 
          success: true, 
          message: 'Docker Compose generated successfully! Now generating CD workflow...' 
        });
        
        // Wait a moment then automatically proceed with the original action
        setTimeout(async () => {
          try {
            console.log('Proceeding with CD workflow generation...');
            const endpoint = originalAction === 'push' ? '/api/cd-workflow/apply' : '/api/cd-workflow/preview';
            console.log('CD workflow endpoint:', endpoint);
            const cdResponse = await apiClient.post(`${endpoint}?repoId=${repoId}`);
            let cdData;
            if (cdResponse && typeof cdResponse.json === 'function') {
              cdData = await cdResponse.json();
            } else if (cdResponse && cdResponse.data) {
              cdData = cdResponse.data;
            } else {
              cdData = cdResponse;
            }
            console.log('CD workflow response:', cdData);
            // Modify the message to show both Docker Compose and CD workflow success
            if (cdData.success) {
              setResult({
                ...cdData,
                message: 'Docker Compose pushed successfully! CD workflow also generated and pushed to GitHub.'
              });
            } else {
              setResult(cdData);
            }
          } catch (cdError) {
            console.error('CD workflow generation failed:', cdError);
            setResult({ 
              success: false, 
              message: 'Docker Compose generated, but failed to generate CD workflow: ' + (cdError?.response?.data?.message || cdError.message)
            });
          }
        }, 1500);
      } else {
        setComposeStatus(null);
        setResult({ success: false, message: data.message || 'Failed to generate Docker Compose' });
      }
    } catch (error) {
      console.error('handleGenerateCompose error:', error);
      console.log('Auth state after error:', { isAuthenticated, user: user?.username, hasToken: !!token });
      console.log('Error status:', error?.status);
      setComposeStatus(null);
      setResult({ success: false, message: error?.response?.data?.message || 'Failed to generate Docker Compose' });
    }
    setLoading(false);
  };

  const handlePush = async () => {
    if (!repoId) {
      setResult('Please select a repository.');
      return;
    }
    setLoading(true);
    setResult(null);
    setComposeStatus(null);
    setOriginalAction('push');
    
    try {
      const response = await apiClient.post(`/api/cd-workflow/apply?repoId=${repoId}`);
      let data;
      if (response && typeof response.json === 'function') {
        data = await response.json();
      } else if (response && response.data) {
        data = response.data;
      } else {
        data = response;
      }
      console.log('Push response:', data);
      
      // Check if Docker Compose is missing
      if (data.missingCompose) {
        setComposeStatus(data);
        await checkComposePreview();
      } else {
        setResult(data);
      }
    } catch (error) {
      console.log('Push Error caught:', error);
      
      // Check different possible status locations
      const status = error?.status || error?.response?.status;
      const message = error?.payload?.message || error?.response?.data?.message || error?.message;
      
      console.log('Push Status code:', status);
      console.log('Push Error message:', message);
      
      // Handle 428 status code (Precondition Required) for missing Docker Compose
      if (status === 428) {
        console.log('Docker Compose missing - 428 status detected, showing modal');
        const composeData = {
          success: false,
          missingCompose: true,
          message: 'Docker Compose is missing. Do you want to generate one?'
        };
        setComposeStatus(composeData);
        setLoading(false); // Reset loading state so modal buttons work
        // Automatically fetch the preview
        setTimeout(() => checkComposePreview(), 100);
        return; // Don't set error result
      } else {
        console.log('Not 428 status, setting error result');
        setResult({ success: false, message: message || 'Push failed' });
      }
    }
    setLoading(false);
  };

  return (
    <div className="min-h-screen bg-gray-900 py-10 px-4 flex justify-center items-start">
      <div className="w-full max-w-5xl bg-gray-800/50 backdrop-blur-sm border border-gray-700 rounded-xl p-8 shadow-lg">
        <div className="flex items-center mb-8">
          <div className="w-12 h-12 bg-purple-600/80 rounded-lg flex items-center justify-center mr-4">
            <span className="text-2xl">🚀</span>
          </div>
          <div>
            <h1 className="text-3xl font-bold text-white mb-1">Generate CD Workflow</h1>
            <p className="text-gray-400 text-sm">Preview and push your Continuous Deployment pipeline configuration.</p>
          </div>
        </div>
        <div className="mb-8">
          <p className="text-gray-200 mb-2">
            This page allows you to preview and push your Continuous Deployment (CD) workflow configuration for your selected repository.<br/>
            Our deployment architecture is designed to automate the process of deploying your application to a Virtual Machine (VM) using GitHub Actions. When you trigger a deployment, the workflow will:
          </p>
          <ul className="list-disc list-inside text-gray-300 mb-2 pl-4">
            <li>Build and test your application using your CI pipeline.</li>
            <li>Securely transfer your Docker Compose files to the VM.</li>
            <li>Connect to the VM via SSH and run Docker Compose commands to deploy or update your application.</li>
            <li>Use GitHub Secrets to securely manage your VM credentials and deployment keys.</li>
          </ul>
          <p className="text-gray-200">
            By previewing, you can review the exact YAML configuration that will be used for deployment. When you push, this configuration is committed to your repository, enabling automated deployments for every new release.
          </p>
        </div>
        <div className="mb-6">
          <label className="block text-gray-300 font-semibold mb-2">Select a Repository</label>
            <div className="relative">
              <select
                id="repoSelect"
                className="block appearance-none w-full bg-gray-800 border border-gray-700 text-gray-200 py-2 pl-10 pr-8 rounded leading-tight focus:outline-none focus:bg-gray-700 focus:border-blue-500 transition-shadow shadow-sm hover:border-blue-400"
                value={repoId}
                onChange={e => setRepoId(e.target.value)}
              >
                {repos.map(repo => (
                  <option key={repo.repoId} value={repo.repoId}>
                    {repo.fullName}
                  </option>
                ))}
              </select>
              {/* Only right-side dropdown arrow remains */}
              {/* Dropdown arrow on the right */}
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-3">
                <svg className="fill-current h-4 w-4 text-gray-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"><path d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"/></svg>
              </div>
            </div>
        </div>
        <div className="flex gap-4 mb-8">
          <button
            className="bg-gradient-to-r from-blue-500 to-blue-600 text-white px-6 py-2 rounded-lg font-semibold shadow hover:from-blue-600 hover:to-blue-700 transition-all"
            onClick={handlePreview}
            disabled={loading}
          >
            Preview
          </button>
          <button
            className="bg-gradient-to-r from-green-500 to-green-600 text-white px-6 py-2 rounded-lg font-semibold shadow hover:from-green-600 hover:to-green-700 transition-all"
            onClick={handlePush}
            disabled={loading}
          >
            Push
          </button>
        </div>
        {loading && <p className="text-yellow-400 font-semibold mb-4">Loading...</p>}
        
        {/* Docker Compose Missing Modal */}
        {composeStatus && composeStatus.missingCompose && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
            <div className="relative w-full max-w-4xl mx-auto bg-gray-900 rounded-xl shadow-2xl border border-gray-700 p-8">
              <button
                className="absolute top-4 right-4 text-gray-400 hover:text-gray-200 text-2xl font-bold focus:outline-none"
                onClick={() => setComposeStatus(null)}
                aria-label="Close"
              >
                &times;
              </button>
              
              <div className="text-center mb-6">
                <div className="w-16 h-16 bg-yellow-600/80 rounded-lg flex items-center justify-center mx-auto mb-4">
                  <span className="text-3xl">🐳</span>
                </div>
                <h2 className="text-2xl font-bold text-white mb-2">Docker Compose Missing</h2>
                <p className="text-gray-300 mb-4">
                  Docker Compose is required for CD workflow generation.<br/>
                  Would you like to generate one?
                </p>
              </div>

              {/* Loading indicator while fetching preview */}
              {!composeStatus.composePreview && !composeStatus.error && (
                <div className="mb-6 text-center">
                  <div className="inline-flex items-center px-4 py-2 bg-blue-600/20 rounded-lg">
                    <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-400 mr-3"></div>
                    <span className="text-blue-300">Loading Docker Compose preview...</span>
                  </div>
                  {/* Fallback button after 5 seconds */}
                  <div className="mt-4">
                    <button
                      className="text-blue-400 hover:text-blue-300 text-sm underline"
                      onClick={() => setComposeStatus(prev => ({ 
                        ...prev, 
                        composePreview: { 
                          hasCompose: false,
                          composePreview: 'Preview skipped',
                          message: 'Generate without preview'
                        }
                      }))}
                    >
                      Skip preview and generate Docker Compose
                    </button>
                  </div>
                </div>
              )}

              {/* Preview section - only show when ready */}
              {composeStatus.composePreview && (
                <div className="mb-6">
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="text-lg font-semibold text-white">Preview Docker Compose</h3>
                    <button
                      className="text-blue-400 hover:text-blue-300 text-sm"
                      onClick={() => setShowComposePreview(!showComposePreview)}
                    >
                      {showComposePreview ? 'Hide Preview' : 'Show Preview'}
                    </button>
                  </div>
                  
                  {showComposePreview && (
                    <pre className="bg-gray-800 rounded-lg p-4 text-sm text-green-200 whitespace-pre-wrap max-h-[40vh] overflow-auto border border-gray-700 shadow-inner">
                      <code>{composeStatus.composePreview.composePreview}</code>
                    </pre>
                  )}
                </div>
              )}

              {/* Error loading preview */}
              {composeStatus.error && (
                <div className="mb-6 text-center">
                  <div className="px-4 py-2 bg-red-600/20 rounded-lg border border-red-700">
                    <span className="text-red-300">Failed to load preview: {composeStatus.error}</span>
                  </div>
                </div>
              )}

              <div className="flex gap-4 justify-center">
                {/* Only show Push button when preview is ready */}
                {composeStatus.composePreview && (
                  <button
                    className="bg-gradient-to-r from-green-500 to-green-600 text-white px-6 py-3 rounded-lg font-semibold shadow hover:from-green-600 hover:to-green-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                    onClick={(e) => {
                      console.log('Push Docker Compose button clicked!');
                      console.log('Event:', e);
                      console.log('Loading state:', loading);
                      console.log('Current URL:', window.location.href);
                      e.preventDefault();
                      e.stopPropagation();
                      if (!loading) {
                        console.log('Calling handleGenerateCompose...');
                        handleGenerateCompose();
                      } else {
                        console.log('Button is disabled due to loading state');
                      }
                    }}
                    disabled={loading}
                  >
                    {loading ? 'Generating...' : 'Push Docker Compose to GitHub'}
                  </button>
                )}
                <button
                  className="bg-gray-600 text-white px-6 py-3 rounded-lg font-semibold shadow hover:bg-gray-700 transition-all"
                  onClick={() => setComposeStatus(null)}
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>
        )}

        {/* YAML Modal Preview */}
        {result && result.workflowYaml && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
            <div className="relative w-full max-w-2xl mx-auto bg-gray-900 rounded-xl shadow-2xl border border-gray-700 p-8 flex flex-col items-center">
              <button
                className="absolute top-4 right-4 text-gray-400 hover:text-gray-200 text-2xl font-bold focus:outline-none"
                onClick={() => setResult(null)}
                aria-label="Close preview"
              >
                &times;
              </button>
              <div className="text-center mb-6">
                <div className="text-lg font-semibold tracking-wide text-white mb-2">Preview YAML</div>
                <div className="text-gray-300 text-sm mb-2">This is the deployment workflow that will be committed to your repository.</div>
              </div>
              <pre className="w-full bg-gray-800 rounded-lg p-5 text-sm text-green-200 whitespace-pre-wrap max-h-[60vh] overflow-auto border border-gray-700 shadow-inner">
                <code>{result.workflowYaml}</code>
              </pre>
            </div>
          </div>
        )}
        {/* Push Success/Fail Message */}
        {result && !result.workflowYaml && (
          <div className="mt-4 flex justify-center">
            {result.success ? (
              <div className="bg-green-900/80 border border-green-700 text-green-200 px-6 py-4 rounded-xl font-semibold text-lg shadow">
                <span>CD workflow was successfully pushed!</span>
                {result.commitHash && (
                  <div className="text-green-300 text-sm mt-2">Commit Hash: <span className="font-mono">{result.commitHash}</span></div>
                )}
              </div>
            ) : (
              <div className="bg-red-900/80 border border-red-700 text-red-200 px-6 py-4 rounded-xl font-semibold text-lg shadow">
                <span>{typeof result === 'string' ? result : (result.message || 'Failed to push CD workflow.')}</span>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default CDGeneration;
