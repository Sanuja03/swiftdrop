import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import { 
  User, 
  Mail, 
  Phone, 
  Lock, 
  Eye, 
  EyeOff, 
  Building, 
  CheckCircle2, 
  ArrowRight, 
  ShieldCheck, 
  AlertCircle 
} from 'lucide-react';
import Logo from '../components/common/Logo';
import { signupUser } from '../services/api';
import { homePathFor } from '@/shared/auth/session';
import { useAuth } from '../context/AuthContext';
import { GoogleLogin } from '@react-oauth/google';
import './SignupPage.css';

export default function SignupPage() {
  const navigate = useNavigate();
  const { loginWithGoogle } = useAuth();

  // Role: ROLE_CUSTOMER or ROLE_MERCHANT
  const [role, setRole] = useState('ROLE_CUSTOMER');

  // Form Fields
  const [fullName, setFullName] = useState('');
  const [businessName, setBusinessName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [agreeTerms, setAgreeTerms] = useState(true);

  // Status
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successInfo, setSuccessInfo] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    setSuccessInfo(null);

    // Validation
    if (!agreeTerms) {
      setErrorMessage('Please accept the Terms of Service to continue.');
      return;
    }

    if (password !== confirmPassword) {
      setErrorMessage('Passwords do not match. Please re-enter.');
      return;
    }

    if (password.length < 6) {
      setErrorMessage('Password must be at least 6 characters long.');
      return;
    }

    const payloadName = role === 'ROLE_MERCHANT' 
      ? (businessName ? `${businessName.trim()} (${fullName.trim()})` : fullName.trim())
      : fullName.trim();

    if (!payloadName) {
      setErrorMessage(role === 'ROLE_MERCHANT' ? 'Please enter your business name and contact person.' : 'Please enter your full name.');
      return;
    }

    setLoading(true);

    const payload = {
      email: email.trim().toLowerCase(),
      password,
      fullName: payloadName,
      phone: phone.trim(),
      role: role
    };

    const res = await signupUser(payload);
    setLoading(false);

    if (res.success) {
      const trimmedEmail = email.trim().toLowerCase();
      setSuccessInfo({
        role,
        message: `Account created successfully! We have sent a 6-digit verification code to ${trimmedEmail}. Redirecting to verification...`
      });
      // Clear sensitive fields
      setPassword('');
      setConfirmPassword('');

      setTimeout(() => {
        navigate(`/verify-email?email=${encodeURIComponent(trimmedEmail)}`);
      }, 1600);
    } else {
      setErrorMessage(res.error || 'Failed to register account. Please check your inputs.');
    }
  };

  const handleGoogleSuccess = async (credentialResponse) => {
    setErrorMessage('');
    setSuccessInfo(null);
    setLoading(true);
    if (!credentialResponse?.credential) {
      setErrorMessage('Google token was not returned.');
      setLoading(false);
      return;
    }
    const res = await loginWithGoogle(credentialResponse.credential);
    setLoading(false);
    if (res.success) {
      navigate(homePathFor(res.user));
    } else {
      setErrorMessage(res.error || 'Google signup failed. Please try with email.');
    }
  };

  return (
    <div className="signup-page-container">
      {/* 1. Dark Navy Header (matching Landing Page) */}
      <header className="signup-header">
        <div className="signup-header-inner">
          <Link to="/" className="signup-brand-link">
            <Logo size="medium" dark={true} />
          </Link>
          
          <nav className="signup-nav-links">
            <Link to="/" className="signup-nav-item">Home</Link>
            <Link to="/#tracking-dashboard" className="signup-nav-item">Track Parcel</Link>
            <Link to="/#services" className="signup-nav-item">Services</Link>
          </nav>

          <div className="signup-header-actions">
            <Link to="/login" className="btn-signup-login">
              Sign In
            </Link>
          </div>
        </div>
      </header>

      {/* 2. Main Registration Card Area */}
      <main className="signup-main">
        <motion.div 
          className="signup-card"
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: "easeOut" }}
        >
          {/* Left Brand Panel (Deep Navy Gradient) */}
          <div className="signup-left-panel">
            <div>
              <div className="signup-badge-pill">
                <span className="signup-badge-dot"></span>
                Join SwiftDrop Network
              </div>

              <h1 className="signup-left-title">
                Start Delivering Across Sri Lanka Today
              </h1>
              <p className="signup-left-subtitle">
                Whether you're sending a personal gift or running an online store, SwiftDrop makes island-wide parcel delivery effortless.
              </p>

              <div className="signup-feature-list">
                <div className="signup-feature-item">
                  <CheckCircle2 size={18} color="#16A34A" />
                  <div>
                    <strong>Instant Tracking & SMS Alerts</strong>
                    <p>Live tracking links and automated SMS updates for sender and recipient.</p>
                  </div>
                </div>
                <div className="signup-feature-item">
                  <CheckCircle2 size={18} color="#16A34A" />
                  <div>
                    <strong>Hassle-Free Cash on Delivery</strong>
                    <p>Reliable doorstep cash collection with daily direct bank payouts for sellers.</p>
                  </div>
                </div>
                <div className="signup-feature-item">
                  <CheckCircle2 size={18} color="#16A34A" />
                  <div>
                    <strong>Free Doorstep Pickup</strong>
                    <p>Schedule parcel pickups directly from your home, boutique, or shop.</p>
                  </div>
                </div>
              </div>
            </div>

            <div className="signup-security-note">
              <ShieldCheck size={20} color="#F58220" />
              <span>Safe, insured transit and photo proof of delivery at drop-off.</span>
            </div>
          </div>

          {/* Right Form Panel (Surface White) */}
          <div className="signup-right-panel">
            <div className="signup-form-container">
              <div className="signup-form-header">
                <h2 className="signup-form-title">Create an Account</h2>
                <p className="signup-form-subtitle">Choose your account type to get started</p>
              </div>

              {/* Account Type Toggle with Motion */}
              <div className="signup-role-toggle">
                <button
                  type="button"
                  className={`signup-role-btn ${role === 'ROLE_CUSTOMER' ? 'active' : ''}`}
                  onClick={() => { setRole('ROLE_CUSTOMER'); setErrorMessage(''); }}
                >
                  <User size={16} />
                  <span>Personal Customer</span>
                </button>
                <button
                  type="button"
                  className={`signup-role-btn ${role === 'ROLE_MERCHANT' ? 'active' : ''}`}
                  onClick={() => { setRole('ROLE_MERCHANT'); setErrorMessage(''); }}
                >
                  <Building size={16} />
                  <span>Business / Merchant</span>
                </button>
              </div>

              {/* Merchant Note Callout */}
              <AnimatePresence>
                {role === 'ROLE_MERCHANT' && (
                  <motion.div 
                    className="signup-merchant-notice"
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: 'auto' }}
                    exit={{ opacity: 0, height: 0 }}
                  >
                    <ShieldCheck size={18} color="#0369A1" style={{ flexShrink: 0, marginTop: 2 }} />
                    <span>
                      <strong>Quick Store Verification:</strong> To safeguard your store and set up daily Cash-on-Delivery (COD) bank payouts, our onboarding team reviews and activates business accounts within 24 hours.
                    </span>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Error Alert */}
              <AnimatePresence>
                {errorMessage && (
                  <motion.div 
                    className="signup-alert-error"
                    initial={{ opacity: 0, y: -8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                  >
                    <AlertCircle size={18} color="#DC2626" />
                    <span>{errorMessage}</span>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Success Alert */}
              <AnimatePresence>
                {successInfo && (
                  <motion.div 
                    className="signup-alert-success"
                    initial={{ opacity: 0, y: -8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                  >
                    <CheckCircle2 size={20} color="#16A34A" style={{ flexShrink: 0, marginTop: 2 }} />
                    <div>
                      <strong>Registration Complete!</strong>
                      <p>{successInfo.message}</p>
                      {successInfo.role === 'ROLE_CUSTOMER' && (
                        <span className="signup-redirect-hint">Redirecting to login in a moment...</span>
                      )}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Official Google OAuth 2.0 Button for Customers */}
              {role === 'ROLE_CUSTOMER' && (
                <>
                  <div className="google-oauth-row">
                    <GoogleLogin
                      onSuccess={handleGoogleSuccess}
                      onError={() => setErrorMessage('Google Sign-In was cancelled or failed.')}
                      shape="rectangular"
                      theme="outline"
                      size="large"
                      text="signup_with"
                      width="100%"
                    />
                  </div>

                  <div className="signup-divider">
                    <span className="signup-divider-line"></span>
                    <span className="signup-divider-text">or register with email</span>
                    <span className="signup-divider-line"></span>
                  </div>
                </>
              )}

              {/* Registration Form */}
              <form onSubmit={handleSubmit} className="signup-form">
                {role === 'ROLE_MERCHANT' ? (
                  <>
                    <div className="signup-input-group">
                      <label className="signup-label">Business / Shop Name</label>
                      <div className="signup-input-wrapper">
                        <Building size={18} className="signup-input-icon" />
                        <input
                          type="text"
                          placeholder="Your business name"
                          value={businessName}
                          onChange={(e) => setBusinessName(e.target.value)}
                          required
                          className="signup-input"
                        />
                      </div>
                    </div>

                    <div className="signup-input-group">
                      <label className="signup-label">Contact Person Full Name</label>
                      <div className="signup-input-wrapper">
                        <User size={18} className="signup-input-icon" />
                        <input
                          type="text"
                          placeholder="Your full name"
                          value={fullName}
                          onChange={(e) => setFullName(e.target.value)}
                          required
                          className="signup-input"
                        />
                      </div>
                    </div>
                  </>
                ) : (
                  <div className="signup-input-group">
                    <label className="signup-label">Full Name</label>
                    <div className="signup-input-wrapper">
                      <User size={18} className="signup-input-icon" />
                      <input
                        type="text"
                        placeholder="Your full name"
                        value={fullName}
                        onChange={(e) => setFullName(e.target.value)}
                        required
                        className="signup-input"
                      />
                    </div>
                  </div>
                )}

                <div className="signup-input-row">
                  <div className="signup-input-group">
                    <label className="signup-label">Email Address</label>
                    <div className="signup-input-wrapper">
                      <Mail size={18} className="signup-input-icon" />
                      <input
                        type="email"
                        placeholder="name@example.com"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                        className="signup-input"
                      />
                    </div>
                  </div>

                  <div className="signup-input-group">
                    <label className="signup-label">Phone Number</label>
                    <div className="signup-input-wrapper">
                      <Phone size={18} className="signup-input-icon" />
                      <input
                        type="tel"
                        placeholder="077 123 4567"
                        value={phone}
                        onChange={(e) => setPhone(e.target.value)}
                        required
                        className="signup-input"
                      />
                    </div>
                  </div>
                </div>

                <div className="signup-input-row">
                  <div className="signup-input-group">
                    <label className="signup-label">Password</label>
                    <div className="signup-input-wrapper">
                      <Lock size={18} className="signup-input-icon" />
                      <input
                        type={showPassword ? 'text' : 'password'}
                        placeholder="Min 6 characters"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                        className="signup-input"
                      />
                      <button
                        type="button"
                        className="signup-eye-btn"
                        onClick={() => setShowPassword(!showPassword)}
                        aria-label={showPassword ? 'Hide password' : 'Show password'}
                      >
                        {showPassword ? <EyeOff size={18} color="#64748B" /> : <Eye size={18} color="#64748B" />}
                      </button>
                    </div>
                  </div>

                  <div className="signup-input-group">
                    <label className="signup-label">Confirm Password</label>
                    <div className="signup-input-wrapper">
                      <Lock size={18} className="signup-input-icon" />
                      <input
                        type={showPassword ? 'text' : 'password'}
                        placeholder="Repeat password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        required
                        className="signup-input"
                      />
                    </div>
                  </div>
                </div>

                <div className="signup-terms-row">
                  <label className="signup-checkbox-label">
                    <input
                      type="checkbox"
                      checked={agreeTerms}
                      onChange={(e) => setAgreeTerms(e.target.checked)}
                      className="signup-checkbox"
                    />
                    <span>
                      I agree to the <a href="#terms" className="signup-terms-link">Terms of Service</a> & <a href="#privacy" className="signup-terms-link">Privacy Policy</a>
                    </span>
                  </label>
                </div>

                <motion.button
                  type="submit"
                  disabled={loading}
                  className="signup-submit-btn"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  {loading ? 'Creating Account...' : (role === 'ROLE_MERCHANT' ? 'Register Merchant Account' : 'Create Customer Account')}
                  <ArrowRight size={18} style={{ marginLeft: 8 }} />
                </motion.button>
              </form>

              {/* Login Callout */}
              <div className="signup-footer-callout">
                <span>Already have an account? </span>
                <Link to="/login" className="signup-login-link">
                  Sign In here
                </Link>
              </div>
            </div>
          </div>
        </motion.div>
      </main>

      {/* 3. Footer (matching Landing Page) */}
      <footer className="signup-footer">
        <div className="signup-footer-inner">
          <p>© {new Date().getFullYear()} SwiftDrop Logistics • IN3101: Enterprise Application Development</p>
          <div className="signup-footer-links">
            <Link to="/">Home</Link>
            <Link to="/#tracking-dashboard">Track Parcel</Link>
            <Link to="/login">Sign In</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
