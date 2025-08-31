
import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';

const CDGeneration = () => {
  const { apiClient } = useAuth();
  const [repoId, setRepoId] = useState('');
  const [repos, setRepos] = useState([]);
  const [reposLoading, setReposLoading] = useState(true);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);

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
      setResult(data);
    } catch (error) {
      setResult(error?.response?.data?.message || 'Preview failed');
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
      setResult(data);
    } catch (error) {
      setResult(error?.response?.data?.message || 'Push failed');
    }
    setLoading(false);
  };

  return (
    <div className="min-h-screen bg-gray-900 py-10 px-4 flex justify-center items-start">
      <div className="w-full max-w-xl bg-gray-800/50 backdrop-blur-sm border border-gray-700 rounded-xl p-8 shadow-lg">
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
