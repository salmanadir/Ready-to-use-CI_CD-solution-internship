// src/components/DeleteAccountModal/DeleteAccountModal.jsx
import React, { useState } from 'react';
import './DeleteAccountModal.css';

const DeleteAccountModal = ({ isOpen, onClose, onConfirm, username, isDeleting }) => {
  const [step, setStep] = useState(1); // 1: warning, 2: username confirmation
  const [usernameInput, setUsernameInput] = useState('');
  const [error, setError] = useState('');

  const handleNextStep = () => {
    setStep(2);
    setError('');
  };

  const handleConfirm = () => {
    if (usernameInput !== username) {
      setError(`Please type "${username}" exactly to confirm`);
      return;
    }
    onConfirm();
  };

  const handleClose = () => {
    setStep(1);
    setUsernameInput('');
    setError('');
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay">
      <div className="modal-container">
        {step === 1 ? (
          // Step 1: Warning
          <div className="modal-content">
            <div className="modal-header">
              <div className="warning-icon">⚠️</div>
              <h2>Delete Account</h2>
              <button className="close-button" onClick={handleClose}>×</button>
            </div>

            <div className="modal-body">
              <p className="warning-text">
                Are you sure you want to delete your account? This action cannot be undone.
              </p>

              <div className="consequences-list">
                <h4>This will permanently:</h4>
                <ul>
                  <li>🗑️ Delete all your data</li>
                  <li>🔒 Revoke GitHub access</li>
                  <li>📊 Remove all repositories and pipelines</li>
                  <li>❌ Cannot be recovered</li>
                </ul>
              </div>
            </div>

            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={handleClose}>
                Cancel
              </button>
              <button className="btn btn-danger" onClick={handleNextStep}>
                Continue
              </button>
            </div>
          </div>
        ) : (
          // Step 2: Username Confirmation
          <div className="modal-content">
            <div className="modal-header">
              <div className="danger-icon">🚨</div>
              <h2>Confirm Deletion</h2>
              <button className="close-button" onClick={handleClose}>×</button>
            </div>

            <div className="modal-body">
              <p className="confirmation-text">
                To confirm, type <strong>"{username}"</strong> in the box below:
              </p>

              <div className="input-group">
                <input
                  type="text"
                  className={`confirmation-input ${error ? 'error' : ''}`}
                  placeholder={`Type "${username}" here`}
                  value={usernameInput}
                  onChange={(e) => {
                    setUsernameInput(e.target.value);
                    setError('');
                  }}
                  disabled={isDeleting}
                  autoFocus
                />
                {error && <span className="error-message">{error}</span>}
              </div>
            </div>

            <div className="modal-footer">
              <button 
                className="btn btn-secondary" 
                onClick={handleClose}
                disabled={isDeleting}
              >
                Cancel
              </button>
              <button 
                className="btn btn-danger" 
                onClick={handleConfirm}
                disabled={isDeleting || usernameInput !== username}
              >
                {isDeleting ? (
                  <>
                    <div className="spinner-small"></div>
                    Deleting...
                  </>
                ) : (
                  'Delete Account'
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default DeleteAccountModal;