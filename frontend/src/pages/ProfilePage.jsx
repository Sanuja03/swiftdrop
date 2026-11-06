import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { 
  User, 
  Mail, 
  Phone, 
  Shield, 
  Key, 
  LogOut, 
  CheckCircle, 
  Clock, 
  AlertTriangle, 
  Terminal, 
  ExternalLink 
} from 'lucide-react';
import Logo from '../components/common/Logo';
import { useAuth } from '../context/AuthContext';
import { testEndpoint } from '../services/api';
import './ProfilePage.css';

export default function ProfilePage() {
  const { user, token, logout } = useAuth();
  const navigate = useNavigate();

  const [testResult, setTestResult] = useState(null);
  const [testingEndpoint, setTestingEndpoint] = useState(null);

  const handleLogout = () => {
    logout();
    navigate('/login');
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
      <div className="profile-container">
        <div className="profile-card empty">
          <p>No user session detected. Please <Link to="/login">log in</Link>.</p>
        </div>
      </div>
    );
  }

  // Get initials for avatar
  const initials = user.fullName
    ? user.fullName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase()
    : 'SD';

  const getRoleBadgeClass = (role) => {
    switch (role) {
      case 'ROLE_ADMIN': return 'badge-admin';
      case 'ROLE_MERCHANT': return 'badge-merchant';
      case 'ROLE_COURIER': return 'badge-courier';
      default: return 'badge-customer';
    }
  };

  return (
    <div className="profile-page-container">
      {/* Header */}
      <header className="profile-header">
        <div className="profile-header-inner">
          <Link to="/" style={{ textDecoration: 'none' }}>
            <Logo size="medium" />
          </Link>
          <div className="profile-header-actions">
            <Link to="/" className="profile-nav-link">Home</Link>
            <button onClick={handleLogout} className="profile-logout-btn">
              <LogOut size={16} />
              <span>Sign Out</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="profile-main">
        <div className="profile-grid">
          {/* User Overview Card */}
          <div className="profile-card user-card">
            <div className="profile-banner"></div>
            <div className="profile-avatar-row">
              <div className="profile-avatar">
                {initials}
              </div>
              <div className="profile-identity">
                <h1 className="profile-name">{user.fullName || 'SwiftDrop User'}</h1>
                <p className="profile-email">{user.email}</p>
              </div>
            </div>

            <div className="profile-badges-row">
              <span className={`profile-badge ${getRoleBadgeClass(user.role)}`}>
                <Shield size={14} />
                {user.role}
              </span>
              <span className={`profile-badge ${user.status === 'ACTIVE' ? 'badge-active' : 'badge-pending'}`}>
                {user.status === 'ACTIVE' ? <CheckCircle size={14} /> : <Clock size={14} />}
                {user.status}
              </span>
              <span className="profile-badge badge-provider">
                Provider: {user.provider || 'LOCAL'}
              </span>
            </div>

            <div className="profile-info-divider"></div>

            <div className="profile-details-list">
              <div className="profile-detail-item">
                <div className="profile-detail-icon">
                  <Mail size={16} />
                </div>
                <div>
                  <span className="profile-detail-label">Email Address</span>
                  <p className="profile-detail-value">{user.email}</p>
                </div>
              </div>

              <div className="profile-detail-item">
                <div className="profile-detail-icon">
                  <Phone size={16} />
                </div>
                <div>
                  <span className="profile-detail-label">Contact Phone</span>
                  <p className="profile-detail-value">{user.phone || 'Not specified'}</p>
                </div>
              </div>

              <div className="profile-detail-item">
                <div className="profile-detail-icon">
                  <Key size={16} />
                </div>
                <div>
                  <span className="profile-detail-label">Account Identifier</span>
                  <p className="profile-detail-value mono">{user.id || 'N/A'}</p>
                </div>
              </div>
            </div>
          </div>

          {/* RBAC Security Live Verification Card */}
          <div className="profile-card rbac-card">
            <div className="rbac-header">
              <div className="rbac-header-icon">
                <Terminal size={20} color="#F58220" />
              </div>
              <div>
                <h2 className="rbac-title">RBAC Live Security Verification</h2>
                <p className="rbac-subtitle">
                  Test role enforcement against Spring Security filter chains with your current JWT.
                </p>
              </div>
            </div>

            <div className="rbac-endpoints-list">
              <button
                className="rbac-btn"
                disabled={testingEndpoint !== null}
                onClick={() => runRbacTest('/user/profile', 'Profile Endpoint (All Authenticated)')}
              >
                <div className="rbac-btn-left">
                  <span className="method-tag get">GET</span>
                  <span className="endpoint-path">/api/v1/user/profile</span>
                </div>
                <span className="expected-badge">Any Logged In</span>
              </button>

              <button
                className="rbac-btn"
                disabled={testingEndpoint !== null}
                onClick={() => runRbacTest('/customer/orders', 'Customer Orders (ROLE_CUSTOMER only)')}
              >
                <div className="rbac-btn-left">
                  <span className="method-tag get">GET</span>
                  <span className="endpoint-path">/api/v1/customer/orders</span>
                </div>
                <span className="expected-badge">Customer Only</span>
              </button>

              <button
                className="rbac-btn"
                disabled={testingEndpoint !== null}
                onClick={() => runRbacTest('/merchant/dashboard', 'Merchant Dashboard (ROLE_MERCHANT only)')}
              >
                <div className="rbac-btn-left">
                  <span className="method-tag get">GET</span>
                  <span className="endpoint-path">/api/v1/merchant/dashboard</span>
                </div>
                <span className="expected-badge">Merchant Only</span>
              </button>

              <button
                className="rbac-btn"
                disabled={testingEndpoint !== null}
                onClick={() => runRbacTest('/courier/deliveries', 'Courier Deliveries (ROLE_COURIER only)')}
              >
                <div className="rbac-btn-left">
                  <span className="method-tag get">GET</span>
                  <span className="endpoint-path">/api/v1/courier/deliveries</span>
                </div>
                <span className="expected-badge">Courier Only</span>
              </button>

              <button
                className="rbac-btn"
                disabled={testingEndpoint !== null}
                onClick={() => runRbacTest('/admin/dashboard', 'Admin Dashboard (ROLE_ADMIN only)')}
              >
                <div className="rbac-btn-left">
                  <span className="method-tag get">GET</span>
                  <span className="endpoint-path">/api/v1/admin/dashboard</span>
                </div>
                <span className="expected-badge">Admin Only</span>
              </button>
            </div>

            {/* Test Output Console */}
            {testResult && (
              <div className={`rbac-console ${testResult.ok ? 'console-success' : 'console-denied'}`}>
                <div className="console-title-bar">
                  <span>
                    HTTP {testResult.status} {testResult.ok ? 'OK (Authorized)' : 'FORBIDDEN / DENIED'}
                  </span>
                  <span className="console-path">{testResult.endpoint}</span>
                </div>
                <pre className="console-output">
                  {JSON.stringify(testResult.data, null, 2)}
                </pre>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

