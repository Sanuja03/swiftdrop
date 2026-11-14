import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import { 
  User, 
  Mail, 
  Phone, 
  ShieldCheck, 
  Key, 
  LogOut, 
  CheckCircle2, 
  Clock, 
  Home, 
  ChevronDown, 
  ChevronUp, 
  Terminal,
  Edit2,
  Save,
  AlertCircle
} from 'lucide-react';
import Logo from '../components/common/Logo';
import { useAuth } from '../context/AuthContext';
import { testEndpoint, updateUserProfile } from '../services/api';
import './ProfilePage.css';

export default function ProfilePage() {
  const { user, token, logout, updateUserSession } = useAuth();
  const navigate = useNavigate();

  // If user has no phone number, open edit form by default
  const isProfileIncomplete = !user?.phone || user.phone.trim() === '';
  const [isEditing, setIsEditing] = useState(isProfileIncomplete);
  
  // Form state
  const [fullName, setFullName] = useState(user?.fullName || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState('');
  const [saveError, setSaveError] = useState('');

  // Evaluator inspector
  const [showRbacInspector, setShowRbacInspector] = useState(false);
  const [testResult, setTestResult] = useState(null);
  const [testingEndpoint, setTestingEndpoint] = useState(null);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSaveError('');
    setSaveMessage('');

    if (!fullName.trim()) {
      setSaveError('Please enter your full name.');
      return;
    }
    if (!phone.trim()) {
      setSaveError('Please enter your mobile phone number.');
      return;
    }

    setSaving(true);
    const res = await updateUserProfile({ fullName: fullName.trim(), phone: phone.trim() }, token);
    setSaving(false);

    if (res.success && res.data) {
      updateUserSession(res.data);
      setSaveMessage('Your details have been updated successfully!');
      setIsEditing(false);
      setTimeout(() => setSaveMessage(''), 3500);
    } else {
      setSaveError(res.error || 'Failed to update details. Please try again.');
    }
  };

  const runRbacTest = async (endpoint, label) => {
    setTestingEndpoint(endpoint);
    setTestResult(null);

    const res = await testEndpoint(endpoint, token);
    setTestResult({
      endpoint,
      label,
      status: res.status,
      ok: res.ok,
      data: res.data || res.error
    });
    setTestingEndpoint(null);
  };

  if (!user) {
    return (
      <div className="profile-page-container">
        <header className="profile-header">
          <div className="profile-header-inner">
            <Link to="/" className="profile-brand-link">
              <Logo size="medium" dark={true} />
            </Link>
          </div>
        </header>
        <main className="profile-main">
          <div className="profile-success-card">
            <p>No active user session found. Please <Link to="/login" style={{ color: '#F58220', fontWeight: 600 }}>Sign In</Link>.</p>
          </div>
        </main>
      </div>
    );
  }

  // Initials for avatar
  const initials = user.fullName
    ? user.fullName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase()
    : 'SD';

  const formatRole = (role) => {
    if (!role) return 'Customer';
    return role.replace('ROLE_', '').toLowerCase().replace(/^\w/, c => c.toUpperCase());
  };

  return (
    <div className="profile-page-container">
      {/* 1. Dark Navy Header (matching Landing, Login & Signup) */}
      <header className="profile-header">
        <div className="profile-header-inner">
          <Link to="/" className="profile-brand-link">
            <Logo size="medium" dark={true} />
          </Link>
          <div className="profile-header-actions">
            <Link to="/" className="profile-header-home-link">
              <Home size={15} />
              <span>Home</span>
            </Link>
            <button onClick={handleLogout} className="profile-header-logout-btn">
              <LogOut size={15} />
              <span>Sign Out</span>
            </button>
          </div>
        </div>
      </header>

      {/* 2. Main Authentication Confirmation Card */}
      <main className="profile-main">
        <motion.div 
          className="profile-success-card"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, ease: "easeOut" }}
        >
          {/* Top Banner Status */}
          <div className="profile-status-banner">
            <div className="profile-status-icon-bubble">
              <CheckCircle2 size={32} color="#16A34A" />
            </div>
            <h1 className="profile-success-title">Signed In Successfully!</h1>
            <p className="profile-success-subtitle">
              Your identity has been authenticated and your session is active.
            </p>
          </div>

          {/* User Overview Block */}
          <div className="profile-identity-card">
            <div className="profile-avatar-circle">
              {initials}
            </div>
            <div className="profile-identity-info">
              <h2 className="profile-user-name">{user.fullName || 'SwiftDrop User'}</h2>
              <p className="profile-user-email">{user.email}</p>
              
              <div className="profile-pills-row">
                <span className="profile-role-pill">
                  <ShieldCheck size={14} />
                  {formatRole(user.role)}
                </span>
                <span className={`profile-status-pill ${user.status === 'ACTIVE' ? 'active' : 'pending'}`}>
                  {user.status === 'ACTIVE' ? '● Active Account' : '● Pending Approval'}
                </span>
                <span className="profile-provider-pill">
                  Provider: {user.provider || 'LOCAL'}
                </span>
              </div>
            </div>
          </div>

          {/* Save Success Alert */}
          <AnimatePresence>
            {saveMessage && (
              <motion.div 
                className="profile-alert-success"
                initial={{ opacity: 0, y: -8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
              >
                <CheckCircle2 size={18} color="#16A34A" />
                <span>{saveMessage}</span>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Details Form (for New Users or when Editing) */}
          {isEditing ? (
            <motion.div 
              className="profile-edit-box"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
            >
              <div className="profile-edit-header">
                <div>
                  <h3 className="profile-edit-title">
                    {isProfileIncomplete ? 'Complete Your Contact Details' : 'Update Your Contact Details'}
                  </h3>
                  <p className="profile-edit-subtitle">
                    Please provide your name and mobile number to enable doorstep courier pickup and instant SMS parcel delivery tracking.
                  </p>
                </div>
              </div>

              {saveError && (
                <div className="profile-alert-error">
                  <AlertCircle size={18} color="#DC2626" />
                  <span>{saveError}</span>
                </div>
              )}

              <form onSubmit={handleSaveProfile} className="profile-details-form">
                <div className="profile-form-row">
                  <div className="profile-form-group">
                    <label className="profile-form-label">Full Name</label>
                    <div className="profile-input-wrapper">
                      <User size={18} className="profile-input-icon" />
                      <input
                        type="text"
                        value={fullName}
                        onChange={(e) => setFullName(e.target.value)}
                        placeholder="Your full name"
                        required
                        className="profile-input"
                      />
                    </div>
                  </div>

                  <div className="profile-form-group">
                    <label className="profile-form-label">Mobile Phone Number</label>
                    <div className="profile-input-wrapper">
                      <Phone size={18} className="profile-input-icon" />
                      <input
                        type="tel"
                        value={phone}
                        onChange={(e) => setPhone(e.target.value)}
                        placeholder="e.g. 077 123 4567"
                        required
                        className="profile-input"
                      />
                    </div>
                  </div>
                </div>

                <div className="profile-form-actions">
                  <button type="submit" disabled={saving} className="btn-save-details">
                    <Save size={16} />
                    <span>{saving ? 'Saving...' : 'Save & Activate Details'}</span>
                  </button>
                  {!isProfileIncomplete && (
                    <button type="button" onClick={() => setIsEditing(false)} className="btn-cancel-edit">
                      Cancel
                    </button>
                  )}
                </div>
              </form>
            </motion.div>
          ) : (
            /* Confirmed Details Grid */
            <div className="profile-session-block">
              <div className="profile-section-heading-row">
                <h3 className="profile-section-title">Verified Contact Details</h3>
                <button onClick={() => setIsEditing(true)} className="btn-edit-trigger">
                  <Edit2 size={14} />
                  <span>Edit Details</span>
                </button>
              </div>

              <div className="profile-session-grid">
                <div className="session-item">
                  <span className="session-label">Full Name</span>
                  <strong className="session-value">{user.fullName || 'Not specified'}</strong>
                </div>
                <div className="session-item">
                  <span className="session-label">Mobile Phone</span>
                  <strong className="session-value highlight-phone">{user.phone || 'Not specified'}</strong>
                </div>
                <div className="session-item">
                  <span className="session-label">Email Address</span>
                  <strong className="session-value">{user.email}</strong>
                </div>
                <div className="session-item">
                  <span className="session-label">Authentication Method</span>
                  <strong className="session-value">
                    {user.provider === 'GOOGLE' ? 'Google OAuth 2.0' : 'Email & Password'}
                  </strong>
                </div>
              </div>
            </div>
          )}

          {/* Action Navigation Buttons */}
          <div className="profile-actions-row">
            <Link to="/" className="btn-profile-primary">
              <Home size={18} />
              <span>Go to Home Page</span>
            </Link>
            <button onClick={handleLogout} className="btn-profile-secondary">
              <LogOut size={18} />
              <span>Sign Out</span>
            </button>
          </div>

          {/* Collapsible Evaluation / RBAC Inspector */}
          <div className="profile-inspector-wrapper">
            <button 
              type="button" 
              onClick={() => setShowRbacInspector(!showRbacInspector)}
              className="btn-inspector-toggle"
            >
              <Terminal size={15} />
              <span>Inspect Security Token & Live RBAC (Evaluation Tools)</span>
              {showRbacInspector ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>

            <AnimatePresence>
              {showRbacInspector && (
                <motion.div 
                  className="profile-inspector-panel"
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  transition={{ duration: 0.25 }}
                >
                  <p className="inspector-intro">
                    Test live backend authorization endpoints with your current JWT access token to verify Role-Based Access Control (RBAC):
                  </p>

                  <div className="rbac-test-chips">
                    {[
                      { path: '/user/profile', label: 'User Profile (Any Role)', expected: 'Allow' },
                      { path: '/customer/orders', label: 'Customer Area', expected: 'Customer Only' },
                      { path: '/merchant/dashboard', label: 'Merchant Dashboard', expected: 'Merchant Only' },
                      { path: '/courier/deliveries', label: 'Courier Portal', expected: 'Courier Only' },
                      { path: '/admin/dashboard', label: 'Admin Console', expected: 'Admin Only' }
                    ].map(ep => (
                      <button
                        key={ep.path}
                        onClick={() => runRbacTest(ep.path, ep.label)}
                        disabled={testingEndpoint === ep.path}
                        className="rbac-test-chip"
                      >
                        <code>{ep.path}</code>
                        <span className="chip-label">{ep.label}</span>
                      </button>
                    ))}
                  </div>

                  {testResult && (
                    <div className={`inspector-result-box ${testResult.ok ? 'allow' : 'deny'}`}>
                      <div className="result-header">
                        <strong>Live API Response: <code>{testResult.endpoint}</code></strong>
                        <span className={`result-status-code status-${testResult.status}`}>
                          HTTP {testResult.status} {testResult.ok ? 'OK (Authorized)' : 'Denied (Blocked by Security)'}
                        </span>
                      </div>
                      <pre className="result-json">
                        {JSON.stringify(testResult.data, null, 2)}
                      </pre>
                    </div>
                  )}
                </motion.div>
              )}
            </AnimatePresence>
          </div>

        </motion.div>
      </main>
    </div>
  );
}
