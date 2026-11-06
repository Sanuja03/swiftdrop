import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
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
import './SignupPage.css';

export default function SignupPage() {
  const navigate = useNavigate();

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
      setSuccessInfo({
        role,
        message: role === 'ROLE_MERCHANT'
          ? 'Merchant account submitted successfully! Your account is pending administrative verification.'
          : 'Customer account created successfully! You can now log in to manage your shipments.'
      });
      // Clear sensitive fields
      setPassword('');
      setConfirmPassword('');

      if (role === 'ROLE_CUSTOMER') {
        setTimeout(() => {
          navigate('/login');
        }, 2500);
      }
    } else {
      setErrorMessage(res.error || 'Failed to register account. Please check your inputs.');
    }
  };

  return (
    <div className="signup-page-container">
      {/* Top Header */}
      <header className="signup-header">
        <div className="signup-header-inner">
          <Link to="/" style={{ textDecoration: 'none' }}>
            <Logo size="medium" />
          </Link>
          <div className="signup-header-links">
            <Link to="/" className="signup-header-link">Track Parcel</Link>
            <span className="signup-header-badge">Island-Wide Delivery</span>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="signup-main">
        <div className="signup-card">
          {/* Left Brand Panel */}
          <div className="signup-left-panel">
            <div className="signup-badge-pill">
              <span className="signup-badge-dot"></span>
              Join SwiftDrop Network
            </div>

            <h1 className="signup-left-title">
              Start Delivering Across Sri Lanka Today
            </h1>
            <p className="signup-left-subtitle">
              Whether you need fast doorstep delivery or automated merchant logistics, SwiftDrop provides modern, reliable end-to-end fulfillment.
            </p>

            <div className="signup-feature-list">
              <div className="signup-feature-item">
                <CheckCircle2 size={18} color="#16A34A" />
                <div>
                  <strong>Instant Tracking Codes</strong>
                  <p>Every parcel receives live tracking updates and SMS notifications.</p>
                </div>
              </div>
              <div className="signup-feature-item">
                <CheckCircle2 size={18} color="#16A34A" />
                <div>
                  <strong>Automated COD Remittance</strong>
                  <p>Fast Cash On Delivery settlements directly to your bank account.</p>
                </div>
              </div>
              <div className="signup-feature-item">
                <CheckCircle2 size={18} color="#16A34A" />
                <div>
                  <strong>Modern Developer APIs</strong>
                  <p>Seamlessly integrate Shopify, WooCommerce, or custom platforms.</p>
                </div>
              </div>
            </div>

            <div className="signup-security-note">
              <ShieldCheck size={20} color="#F58220" />
              <span>Encrypted JWT authorization and role-based data security.</span>
            </div>
          </div>

          {/* Right Form Panel */}
          <div className="signup-right-panel">
            <div className="signup-form-container">
              <div className="signup-form-header">
                <h2 className="signup-form-title">Create an Account</h2>
                <p className="signup-form-subtitle">Choose your account type to get started</p>
              </div>

              {/* Account Type Toggle */}
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
              {role === 'ROLE_MERCHANT' && (
                <div className="signup-merchant-notice">
                  <AlertCircle size={18} color="#102A4C" />
                  <span>
                    <strong>Administrative Review Required:</strong> Merchant accounts are created in <em>Pending Approval</em> status and verified by Dispatcher / Admin before activation.
                  </span>
                </div>
              )}

              {/* Error Alert */}
              {errorMessage && (
                <div className="signup-alert-error">
                  <AlertCircle size={18} color="#EF4444" />
                  <span>{errorMessage}</span>
                </div>
              )}

              {/* Success Alert */}
              {successInfo && (
                <div className="signup-alert-success">
                  <CheckCircle2 size={20} color="#16A34A" />
                  <div>
                    <strong>Registration Complete!</strong>
                    <p>{successInfo.message}</p>
                    {successInfo.role === 'ROLE_CUSTOMER' && (
                      <span className="signup-redirect-hint">Redirecting to login in a moment...</span>
                    )}
                  </div>
                </div>
              )}

              {/* Registration Form */}
              <form onSubmit={handleSubmit} className="signup-form">
                {role === 'ROLE_MERCHANT' ? (
                  <>
                    <div className="signup-input-group">
                      <label className="signup-label">Business / Shop Name</label>
                      <div className="signup-input-wrapper">
                        <Building size={18} color="#64748B" className="signup-input-icon" />
                        <input
                          type="text"
                          placeholder="e.g. Colombo Tech Store"
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
                        <User size={18} color="#64748B" className="signup-input-icon" />
                        <input
                          type="text"
                          placeholder="e.g. Kasun Perera"
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
                      <User size={18} color="#64748B" className="signup-input-icon" />
                      <input
                        type="text"
                        placeholder="e.g. Kasun Perera"
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
                      <Mail size={18} color="#64748B" className="signup-input-icon" />
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
                      <Phone size={18} color="#64748B" className="signup-input-icon" />
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
                      <Lock size={18} color="#64748B" className="signup-input-icon" />
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
                      <Lock size={18} color="#64748B" className="signup-input-icon" />
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

                <button
                  type="submit"
                  disabled={loading}
                  className="signup-submit-btn"
                >
                  {loading ? 'Creating Account...' : (role === 'ROLE_MERCHANT' ? 'Register Merchant Account' : 'Create Customer Account')}
                  <ArrowRight size={18} style={{ marginLeft: 8 }} />
                </button>
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
        </div>
      </main>
    </div>
  );
}
