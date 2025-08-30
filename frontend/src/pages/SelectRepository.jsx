// src/pages/Dashboard/SelectRepository.jsx
import React from 'react';

const SelectRepository = () => {
  return (
    <div className="min-h-screen bg-gray-900 pt-16"> {/* pt-16 for navbar spacing */}
      <div className="max-w-7xl mx-auto px-6 py-8">
        <div className="text-center py-20">
          <div className="w-16 h-16 bg-blue-500/20 rounded-full flex items-center justify-center mx-auto mb-6">
            <span className="text-3xl">📁</span>
          </div>
          <h1 className="text-3xl font-bold text-white mb-4">
            Select Repository Page
          </h1>
          <p className="text-gray-400 text-lg mb-8">
            This is a placeholder for the Select Repository functionality.
          </p>
          <div className="bg-cyan-500/10 border border-cyan-500/30 rounded-lg p-6 max-w-md mx-auto">
            <p className="text-cyan-400 font-medium">
              ✅ Route is working correctly!
            </p>
            <p className="text-gray-300 text-sm mt-2">
              The SelectRepository component is loaded and ready for development.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SelectRepository;