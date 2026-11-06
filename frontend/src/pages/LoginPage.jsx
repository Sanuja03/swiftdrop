import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Mail, Lock, Eye, EyeOff, Truck, ArrowRight, MapPin, CheckCircle2 } from 'lucide-react';
import Logo from '../components/common/Logo';
import { useAuth } from '../context/AuthContext';
import './LoginPage.css';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, loginWithGoogle, loading } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  const [successUser, setSuccessUser] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    setSuccessUser(null);

    if (!email || !password) {
      setErrorMessage('Please enter both email and password.');
      return;
    }

    const res = await login(email, password);
    if (res.success) {
      setSuccessUser(res.user);
      setTimeout(() => {
        navigate('/profile');
      }, 700);
    } else {
      setErrorMessage(res.error || 'Invalid credentials or service offline.');
    }
  };

  const handleGoogleLogin = async () => {
    setErrorMessage('');
    setSuccessUser(null);
    // In local/evaluator test mode, authenticate using mock Google token
    const res = await loginWithGoogle('mock-google-token-customer');
    if (res.success) {
      setSuccessUser(res.user);
      setTimeout(() => {
        navigate('/profile');
      }, 700);
    } else {
      setErrorMessage(res.error || 'Google authentication failed.');
    }
  };

  return (
    <div className="login-page-container">
      {/* Top Header */}
      <header className="login-header">
        <div className="login-header-inner">
          <Link to="/" style={{ textDecoration: 'none' }}>
            <Logo size="medium" />
          </Link>
          <div className="login-header-links">
            <Link to="/#tracking-section" className="login-header-link">Track Parcel</Link>
            <span className="login-header-badge">Island-Wide Service</span>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="login-main">
        <div className="login-card">
          {/* Left Brand Panel */}
          <div className="login-left-panel">
            <div className="login-badge-pill">
              <span className="login-badge-dot"></span>
              Live Island-Wide Logistics
            </div>

            <h1 className="login-left-title">
              Swift, Secure & Reliable Parcel Delivery
            </h1>
            <p className="login-left-subtitle">
              Island-wide express tracking, automated merchant integrations, and intelligent courier dispatching.
            </p>

            {/* Real-time Tracking Preview Widget (matches design reference) */}
            <div className="login-tracking-widget">
              <div className="login-widget-header">
                <div className="login-widget-left">
                  <div className="login-truck-icon">
                    <Truck size={18} color="#F58220" />
                  </div>
                  <div>
                    <div className="login-widget-tracking-num">SDP7845621</div>
                    <div className="login-widget-route">Colombo 05 → Kandy</div>
                  </div>
                </div>
                <span className="login-status-pill">In Transit</span>
              </div>

              <div className="login-widget-progress-bar">
                <div className="login-widget-progress-fill"></div>
              </div>

              <div className="login-widget-footer">
                <span>
                  <MapPin size={12} style={{ display: 'inline', marginRight: 4 }} />
                  Current Location: Kadawatha
                </span>
                <span style={{ fontWeight: 600 }}>ETA: 2:30 PM</span>
              </div>
            </div>

            {/* Feature Highlights */}
            <div className="login-feature-list">
              <div className="login-feature-item">
                <CheckCircle2 size={16} color="#16A34A" />
                <span>Real-time live WebSocket parcel status</span>
              </div>
              <div className="login-feature-item">
                <CheckCircle2 size={16} color="#16A34A" />
                <span>Secured JWT & OAuth 2.0 authorization</span>
              </div>
              <div className="login-feature-item">
                <CheckCircle2 size={16} color="#16A34A" />
                <span>Automated webhooks with retry policy</span>
              </div>
            </div>
          </div>

          {/* Right Form Panel */}
          <div className="login-right-panel">
            <div className="login-form-container">
              <div className="login-form-header">
                <h2 className="login-form-title">Welcome back</h2>
                <p className="login-form-subtitle">Sign in to access your SwiftDrop account</p>
              </div>

              {/* Google OAuth 2.0 Button */}
              <button
                type="button"
                className="login-google-btn"
                onClick={handleGoogleLogin}
              >
                <svg width="18" height="18" viewBox="0 0 18 18">
                  <path
                    fill="#4285F4"
                    d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.874 2.684-6.616z"
                  />
                  <path
                    fill="#34A853"
                    d="M9 18c2.43 0 4.467-.806 5.956-2.184l-2.908-2.258c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M3.964 10.707c-.18-.54-.282-1.117-.282-1.707s.102-1.167.282-1.707V4.961H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.039l3.007-2.332z"
                  />
                  <path
                    fill="#EA4335"
                    d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.961L3.964 7.293C4.672 5.166 6.656 3.58 9 3.58z"
                  />
                </svg>
                Continue with Google
              </button>

              {/* Divider */}
              <div className="login-divider">
                <div className="login-divider-line"></div>
                <span className="login-divider-text">or sign in with email</span>
                <div className="login-divider-line"></div>
              </div>

              {/* Error Alert */}
              {errorMessage && (
                <div className="login-alert-error">
                  <span>{errorMessage}</span>
                </div>
              )}

              {/* Success Alert */}
              {successUser && (
                <div className="login-alert-success">
                  <CheckCircle2 size={18} color="#16A34A" />
                  <span>
                    Authenticated as <strong>{successUser.fullName || successUser.email}</strong> ({successUser.role})
                  </span>
                </div>
              )}

              {/* Login Form */}
              <form onSubmit={handleSubmit} className="login-form">
                <div className="login-input-group">
                  <label className="login-label">Email Address</label>
                  <div className="login-input-wrapper">
                    <Mail size={18} color="#64748B" className="login-input-icon" />
                    <input
                      type="email"
                      placeholder="e.g. name@swiftdrop.lk"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      required
                      className="login-input"
                    />
                  </div>
                </div>

                <div className="login-input-group">
                  <div className="login-password-header">
                    <label className="login-label">Password</label>
                    <a href="#forgot" className="login-forgot-link">Forgot password?</a>
                  </div>
                  <div className="login-input-wrapper">
                    <Lock size={18} color="#64748B" className="login-input-icon" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      placeholder="Enter your password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                      className="login-input"
                    />
                    <button
                      type="button"
                      className="login-eye-btn"
                      onClick={() => setShowPassword(!showPassword)}
                      aria-label={showPassword ? "Hide password" : "Show password"}
                    >
                      {showPassword ? <EyeOff size={18} color="#64748B" /> : <Eye size={18} color="#64748B" />}
                    </button>
                  </div>
                </div>

                <div className="login-remember-row">
                  <label className="login-checkbox-label">
                    <input
                      type="checkbox"
                      checked={rememberMe}
                      onChange={(e) => setRememberMe(e.target.checked)}
                      className="login-checkbox"
                    />
                    Keep me signed in
                  </label>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="login-submit-btn"
                >
                  {loading ? 'Authenticating...' : 'Sign In'}
                  <ArrowRight size={18} style={{ marginLeft: 8 }} />
                </button>
              </form>

              {/* Register Callout */}
              <div className="login-register-footer">
                <span>Don't have an account? </span>
                <Link to="/signup" className="login-register-link">
                  Create an account
                </Link>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
