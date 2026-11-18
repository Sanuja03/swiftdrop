import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import {
  Mail,
  Lock,
  Eye,
  EyeOff,
  Truck,
  ArrowRight,
  MapPin,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';
import Logo from '../components/common/Logo';
import { useAuth } from '../context/AuthContext';
import { homePathFor } from '@/shared/auth/session';
import { GoogleLogin } from '@react-oauth/google';
import './LoginPage.css';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  // Back to the page that asked for sign-in, otherwise the user's home (customers: /app).
  const afterLoginPath = (signedInUser) => {
    const from = location.state?.from;
    return from?.pathname && from.pathname !== '/login' ? `${from.pathname}${from.search ?? ''}` : homePathFor(signedInUser);
  };
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
        navigate(afterLoginPath(res.user));
      }, 700);
    } else {
      setErrorMessage(res.error || 'Invalid credentials or service offline.');
    }
  };

  const handleGoogleSuccess = async (credentialResponse) => {
    setErrorMessage('');
    setSuccessUser(null);
    if (!credentialResponse?.credential) {
      setErrorMessage('Google token was not returned.');
      return;
    }
    const res = await loginWithGoogle(credentialResponse.credential);
    if (res.success) {
      setSuccessUser(res.user);
      setTimeout(() => {
        navigate(afterLoginPath(res.user));
      }, 700);
    } else {
      setErrorMessage(res.error || 'Google authentication failed.');
    }
  };

  return (
    <div className="login-page-container">
      {/* 1. Dark Navy Header (matching Landing Page) */}
      <header className="login-header">
        <div className="login-header-inner">
          <Link to="/" className="login-brand-link">
            <Logo size="medium" dark={true} />
          </Link>
          
          <nav className="login-nav-links">
            <Link to="/" className="login-nav-item">Home</Link>
            <Link to="/#tracking-dashboard" className="login-nav-item">Track Parcel</Link>
            <Link to="/#services" className="login-nav-item">Services</Link>
          </nav>

          <div className="login-header-actions">
            <Link to="/signup" className="btn-login-signup">
              Create Account
            </Link>
          </div>
        </div>
      </header>

      {/* 2. Main Login Area */}
      <main className="login-main">
        <motion.div 
          className="login-card"
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: "easeOut" }}
        >
          {/* Left Brand Panel (Deep Navy) */}
          <div className="login-left-panel">
            <div>
              <div className="login-badge-pill">
                <span className="login-badge-dot"></span>
                Live Island-Wide Logistics
              </div>

              <h1 className="login-left-title">
                Swift, Secure & Reliable Parcel Delivery
              </h1>
              <p className="login-left-subtitle">
                Guaranteed island-wide doorstep delivery, live GPS tracking, and dependable courier service across Sri Lanka.
              </p>

              {/* Real-Time Live Tracking Preview Widget */}
              <div className="login-tracking-widget">
                <div className="login-widget-header">
                  <div className="login-widget-left">
                    <div className="login-truck-icon">
                      <Truck size={18} color="#F58220" />
                    </div>
                    <div>
                      <div className="login-widget-tracking-num">SDP7845621</div>
                      <div className="login-widget-route">Colombo Hub → Kandy Central</div>
                    </div>
                  </div>
                  <span className="login-status-pill">In Transit</span>
                </div>

                <div className="login-widget-progress-bar">
                  <motion.div 
                    className="login-widget-progress-fill"
                    initial={{ width: '0%' }}
                    animate={{ width: '65%' }}
                    transition={{ duration: 1.2, delay: 0.3 }}
                  />
                </div>

                <div className="login-widget-footer">
                  <span>
                    <MapPin size={13} style={{ display: 'inline', marginRight: 4, verticalAlign: 'middle' }} />
                    Kadawatha Junction
                  </span>
                  <span style={{ fontWeight: 600 }}>ETA: 2:30 PM</span>
                </div>
              </div>
            </div>

            {/* Feature Highlights */}
            <div className="login-feature-list">
              <div className="login-feature-item">
                <CheckCircle2 size={16} color="#16A34A" />
                <span>Live GPS parcel tracking with instant SMS alerts</span>
              </div>
              <div className="login-feature-item">
                <CheckCircle2 size={16} color="#16A34A" />
                <span>Next-day doorstep delivery to all 25 districts</span>
              </div>
              <div className="login-feature-item">
                <CheckCircle2 size={16} color="#16A34A" />
                <span>Reliable Cash on Delivery (COD) payouts for sellers</span>
              </div>
            </div>
          </div>

          {/* Right Form Panel (Surface White) */}
          <div className="login-right-panel">
            <div className="login-form-container">
              <div className="login-form-header">
                <h2 className="login-form-title">Welcome back</h2>
                <p className="login-form-subtitle">Sign in to access your SwiftDrop account</p>
              </div>

              {/* Official Google OAuth 2.0 Button */}
              <div className="google-oauth-row">
                <GoogleLogin
                  onSuccess={handleGoogleSuccess}
                  onError={() => setErrorMessage('Google Sign-In was cancelled or failed.')}
                  shape="rectangular"
                  theme="outline"
                  size="large"
                  text="continue_with"
                  width="100%"
                />
              </div>

              <div className="login-divider">
                <div className="login-divider-line"></div>
                <span className="login-divider-text">or sign in with email</span>
                <div className="login-divider-line"></div>
              </div>

              {/* Error Alert */}
              <AnimatePresence>
                {errorMessage && (
                  <motion.div 
                    className="login-alert-error"
                    initial={{ opacity: 0, y: -8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    style={{ display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-start' }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <AlertCircle size={18} color="#DC2626" style={{ flexShrink: 0 }} />
                      <span>{errorMessage}</span>
                    </div>
                    {errorMessage.toLowerCase().includes('not verified') && (
                      <Link 
                        to={`/verify-email?email=${encodeURIComponent(email)}`}
                        style={{ color: '#F58220', fontWeight: 600, fontSize: '13px', marginLeft: '28px', textDecoration: 'underline' }}
                      >
                        Click here to verify your email now →
                      </Link>
                    )}
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Success Alert */}
              <AnimatePresence>
                {successUser && (
                  <motion.div 
                    className="login-alert-success"
                    initial={{ opacity: 0, y: -8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                  >
                    <CheckCircle2 size={18} color="#16A34A" />
                    <span>
                      Authenticated as <strong>{successUser.fullName || successUser.email}</strong> ({successUser.role})
                    </span>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Login Form */}
              <form onSubmit={handleSubmit} className="login-form">
                <div className="login-input-group">
                  <label className="login-label">Email Address</label>
                  <div className="login-input-wrapper">
                    <Mail size={18} className="login-input-icon" />
                    <input
                      type="email"
                      placeholder="e.g. customer@swiftdrop.lk"
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
                    <Link to="/forgot-password" className="login-forgot-link">Forgot password?</Link>
                  </div>
                  <div className="login-input-wrapper">
                    <Lock size={18} className="login-input-icon" />
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
                    <span>Keep me signed in</span>
                  </label>
                </div>

                <motion.button
                  type="submit"
                  disabled={loading}
                  className="login-submit-btn"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  {loading ? 'Authenticating...' : 'Sign In'}
                  <ArrowRight size={18} style={{ marginLeft: 8 }} />
                </motion.button>
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
        </motion.div>
      </main>

      {/* 3. Footer (matching Landing Page) */}
      <footer className="login-footer">
        <div className="login-footer-inner">
          <p>© {new Date().getFullYear()} SwiftDrop Logistics • IN3101: Enterprise Application Development</p>
          <div className="login-footer-links">
            <Link to="/">Home</Link>
            <Link to="/#tracking-dashboard">Track Parcel</Link>
            <Link to="/signup">Register Account</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
