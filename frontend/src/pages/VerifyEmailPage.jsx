import { useState, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import {
  Mail,
  CheckCircle2,
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  RefreshCw
} from 'lucide-react';
import Logo from '../components/common/Logo';
import { verifyEmailCode, resendVerificationEmail } from '../services/api';
import { homePathFor } from '@/shared/auth/session';
import { useAuth } from '../context/AuthContext';
import './VerifyEmailPage.css';

export default function VerifyEmailPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { setSession } = useAuth();

  const urlEmail = searchParams.get('email') || '';
  const urlCode = searchParams.get('code') || '';
  // Opened from the email link: verify straight away, so start in the loading state.
  const autoVerify = Boolean(urlEmail && urlCode.length === 6);

  const [email, setEmail] = useState(urlEmail);
  const [isEditingEmail, setIsEditingEmail] = useState(!urlEmail);
  const [code, setCode] = useState(urlCode);

  const [loading, setLoading] = useState(autoVerify);
  const [resending, setResending] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  const [errorMessage, setErrorMessage] = useState('');
  const [infoMessage, setInfoMessage] = useState('');
  const [isSuccess, setIsSuccess] = useState(false);
  const [successData, setSuccessData] = useState(null);

  // Cooldown countdown timer
  useEffect(() => {
    if (cooldown > 0) {
      const timer = setTimeout(() => setCooldown((prev) => prev - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [cooldown]);

  const requestVerification = (targetEmail, targetCode) =>
    verifyEmailCode(targetEmail.trim().toLowerCase(), targetCode.trim());

  // Shows the server's answer to a verification request.
  const showResult = (res) => {
    setLoading(false);

    if (res.success) {
      setIsSuccess(true);
      setSuccessData(res.data);

      if (res.data?.accessToken) {
        setSession(res.data);
        setTimeout(() => {
          navigate(homePathFor(res.data.user));
        }, 2000);
      }
    } else {
      setErrorMessage(res.error || 'Verification failed. Please check the code or request a new one.');
    }
  };

  const performVerification = async (targetEmail, targetCode) => {
    setErrorMessage('');
    setInfoMessage('');
    setLoading(true);
    showResult(await requestVerification(targetEmail, targetCode));
  };

  // Auto-verify if both email and 6-digit code are in query parameters (from clicking email link).
  // The page already starts in the loading state, so state only changes once the server answers.
  useEffect(() => {
    if (autoVerify) {
      requestVerification(urlEmail, urlCode).then(showResult);
    }
  }, [urlEmail, urlCode]);

  const handleManualSubmit = (e) => {
    e.preventDefault();
    if (!email.trim()) {
      setErrorMessage('Please provide your email address.');
      return;
    }
    if (!code.trim() || code.trim().length !== 6) {
      setErrorMessage('Please enter the 6-digit verification code.');
      return;
    }
    performVerification(email, code);
  };

  const handleResend = async () => {
    if (cooldown > 0 || resending) return;
    if (!email.trim()) {
      setErrorMessage('Please enter your email address to receive a new code.');
      return;
    }

    setErrorMessage('');
    setInfoMessage('');
    setResending(true);

    const res = await resendVerificationEmail(email.trim().toLowerCase());
    setResending(false);

    if (res.success) {
      setInfoMessage('A fresh 6-digit verification code has been dispatched to your email.');
      setCooldown(60); // 60 seconds cooldown
    } else {
      setErrorMessage(res.error || 'Failed to resend code. Please try again.');
    }
  };

  return (
    <div className="verify-page-container">
      {/* 1. Header (Consistent Deep Navy) */}
      <header className="verify-header">
        <div className="verify-header-inner">
          <Link to="/" className="verify-brand-link">
            <Logo size="medium" dark={true} />
          </Link>
          <Link to="/login" className="verify-header-back-link">
            <ArrowLeft size={16} />
            <span>Back to Sign In</span>
          </Link>
        </div>
      </header>

      {/* 2. Main Verification Area */}
      <main className="verify-main">
        <motion.div 
          className="verify-card"
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, ease: "easeOut" }}
        >
          {!isSuccess ? (
            <>
              <div className="verify-icon-bubble">
                <Mail size={28} color="#F58220" />
              </div>

              <h1 className="verify-title">Verify Your Email</h1>
              <p className="verify-subtitle">
                We've sent a 6-digit verification code to your email address. Enter it below to activate your SwiftDrop account.
              </p>

              {/* Email Pill Badge */}
              {!isEditingEmail && email ? (
                <div className="verify-email-badge">
                  <span>{email}</span>
                  <button 
                    type="button" 
                    className="verify-email-edit-btn"
                    onClick={() => setIsEditingEmail(true)}
                  >
                    Change
                  </button>
                </div>
              ) : null}

              {/* Error Alert */}
              <AnimatePresence>
                {errorMessage && (
                  <motion.div 
                    className="verify-alert-error"
                    initial={{ opacity: 0, y: -6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -6 }}
                  >
                    <AlertCircle size={18} style={{ flexShrink: 0 }} />
                    <span>{errorMessage}</span>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Info / Resent Alert */}
              <AnimatePresence>
                {infoMessage && (
                  <motion.div 
                    className="verify-alert-info"
                    initial={{ opacity: 0, y: -6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -6 }}
                  >
                    <CheckCircle2 size={18} style={{ flexShrink: 0 }} />
                    <span>{infoMessage}</span>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Verification Form */}
              <form onSubmit={handleManualSubmit} className="verify-form">
                {isEditingEmail && (
                  <div className="verify-input-group">
                    <label className="verify-label">Email Address</label>
                    <div className="verify-input-wrapper">
                      <Mail size={18} className="verify-input-icon" />
                      <input
                        type="email"
                        placeholder="your.email@example.com"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                        className="verify-input"
                      />
                    </div>
                  </div>
                )}

                <div className="verify-input-group">
                  <label className="verify-label">6-Digit Verification Code</label>
                  <input
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    placeholder="• • • • • •"
                    value={code}
                    onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                    required
                    className="verify-input verify-code-input"
                    autoFocus
                  />
                </div>

                {/* Resend Row */}
                <div className="verify-resend-row">
                  <span>Didn't receive the email?</span>
                  <button
                    type="button"
                    className="verify-resend-btn"
                    onClick={handleResend}
                    disabled={cooldown > 0 || resending}
                  >
                    <RefreshCw size={14} className={resending ? 'animate-spin' : ''} />
                    {cooldown > 0 ? `Resend code in ${cooldown}s` : (resending ? 'Sending...' : 'Resend Code')}
                  </button>
                </div>

                <button
                  type="submit"
                  disabled={loading || !email || code.length !== 6}
                  className="verify-submit-btn"
                >
                  {loading ? 'Verifying Code...' : 'Verify Email & Activate'}
                  <ArrowRight size={18} style={{ marginLeft: 8 }} />
                </button>
              </form>

              <div className="verify-footer-text">
                Need help? <Link to="/login" className="verify-footer-link">Back to Sign In</Link> • <Link to="/signup" className="verify-footer-link">Sign up with another email</Link>
              </div>
            </>
          ) : (
            /* Success State */
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.3 }}
            >
              <div className="verify-icon-bubble success">
                <CheckCircle2 size={32} color="#16A34A" />
              </div>

              <h2 className="verify-title" style={{ color: '#16A34A' }}>
                Email Verified!
              </h2>

              <p className="verify-subtitle">
                Your email address <strong>{email}</strong> has been successfully verified.
              </p>

              {successData?.accessToken ? (
                /* Customer or already-approved user: active session */
                <>
                  <div className="verify-success-box">
                    <div className="verify-success-row">
                      <span className="verify-success-label">Account Status</span>
                      <span className="verify-success-val" style={{ color: '#16A34A' }}>Active</span>
                    </div>
                    <div className="verify-success-row">
                      <span className="verify-success-label">Account Type</span>
                      <span className="verify-success-val">{successData?.user?.role?.replace('ROLE_', '')}</span>
                    </div>
                  </div>

                  <p style={{ fontSize: '13px', color: '#64748B', marginBottom: 16 }}>
                    Redirecting you to your SwiftDrop dashboard in a moment...
                  </p>

                  <button
                    type="button"
                    onClick={() => navigate(homePathFor(successData?.user))}
                    className="verify-submit-btn"
                  >
                    Go to Dashboard Now
                    <ArrowRight size={18} style={{ marginLeft: 8 }} />
                  </button>
                </>
              ) : (
                /* Merchant under review */
                <>
                  <div className="verify-success-box">
                    <div className="verify-success-row">
                      <span className="verify-success-label">Store Application</span>
                      <span className="verify-success-val" style={{ color: '#D97706' }}>Pending Verification</span>
                    </div>
                    <div className="verify-success-row">
                      <span className="verify-success-label">Review Window</span>
                      <span className="verify-success-val">Within 24 Hours</span>
                    </div>
                  </div>

                  <p style={{ fontSize: '13.5px', color: '#475569', lineHeight: 1.5, marginBottom: 20 }}>
                    Our dispatch operations team is reviewing your merchant store profile. You will receive an email confirmation once daily COD payouts and your shipping portal are activated.
                  </p>

                  <button
                    type="button"
                    onClick={() => navigate('/login')}
                    className="verify-secondary-btn"
                  >
                    Return to Sign In
                  </button>
                </>
              )}
            </motion.div>
          )}
        </motion.div>
      </main>

      {/* 3. Footer */}
      <footer className="verify-footer">
        <div className="verify-footer-inner">
          <p>© {new Date().getFullYear()} SwiftDrop Logistics • Enterprise Application Development (IN3101)</p>
          <div style={{ display: 'flex', gap: '16px' }}>
            <Link to="/" style={{ color: '#94A3B8', textDecoration: 'none' }}>Home</Link>
            <Link to="/login" style={{ color: '#94A3B8', textDecoration: 'none' }}>Sign In</Link>
            <Link to="/signup" style={{ color: '#94A3B8', textDecoration: 'none' }}>Sign Up</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}

