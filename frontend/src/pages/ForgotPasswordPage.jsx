import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import { 
  Mail, 
  Lock, 
  KeyRound, 
  ArrowLeft, 
  CheckCircle2, 
  AlertCircle, 
  Eye, 
  EyeOff, 
  ShieldCheck,
  RefreshCw
} from 'lucide-react';
import Logo from '../components/common/Logo';
import { requestPasswordReset, resetUserPassword } from '../services/api';
import './ForgotPasswordPage.css';

export default function ForgotPasswordPage() {
  const navigate = useNavigate();

  // Steps: 1 = Enter Email, 2 = Enter Code & New Password, 3 = Success
  const [step, setStep] = useState(1);

  // Form Fields
  const [email, setEmail] = useState('');
  const [resetCode, setResetCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // UI States
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  // Step 1: Request Reset Code
  const handleRequestCode = async (e) => {
    e.preventDefault();
    setErrorMessage('');

    if (!email.trim()) {
      setErrorMessage('Please enter your email address.');
      return;
    }

    setLoading(true);
    const res = await requestPasswordReset(email.trim());
    setLoading(false);

    if (res.success) {
      setResetCode(''); // keep empty for the user to type from their email
      setStep(2);
    } else {
      setErrorMessage(res.error || 'Failed to request reset code. Please try again.');
    }
  };

  // Step 2: Reset Password
  const handleResetPassword = async (e) => {
    e.preventDefault();
    setErrorMessage('');

    if (!resetCode.trim()) {
      setErrorMessage('Please enter the 6-digit verification code.');
      return;
    }
    if (newPassword.length < 6) {
      setErrorMessage('New password must be at least 6 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setErrorMessage('Passwords do not match. Please re-enter.');
      return;
    }

    setLoading(true);
    const res = await resetUserPassword(resetCode.trim(), newPassword);
    setLoading(false);

    if (res.success) {
      setStep(3);
    } else {
      setErrorMessage(res.error || 'Failed to reset password. Please check your verification code.');
    }
  };

  return (
    <div className="forgot-page-container">
      {/* 1. Header (Consistent Navy) */}
      <header className="forgot-header">
        <div className="forgot-header-inner">
          <Link to="/" className="forgot-brand-link">
            <Logo size="medium" dark={true} />
          </Link>
          <Link to="/login" className="forgot-header-back-link">
            <ArrowLeft size={16} />
            <span>Back to Sign In</span>
          </Link>
        </div>
      </header>

      {/* 2. Main Centered Card */}
      <main className="forgot-main">
        <motion.div 
          className="forgot-card"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: "easeOut" }}
        >
          {/* STEP 1: Enter Email */}
          {step === 1 && (
            <div>
              <div className="forgot-icon-bubble">
                <KeyRound size={28} color="#F58220" />
              </div>
              <h1 className="forgot-title">Forgot Your Password?</h1>
              <p className="forgot-subtitle">
                Don’t worry! Enter your registered email address and we will generate a 6-digit verification code to reset your password.
              </p>

              {/* Error Alert */}
              <AnimatePresence>
                {errorMessage && (
                  <motion.div 
                    className="forgot-alert-error"
                    initial={{ opacity: 0, y: -6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -6 }}
                  >
                    <AlertCircle size={17} color="#DC2626" />
                    <span>{errorMessage}</span>
                  </motion.div>
                )}
              </AnimatePresence>

              <form onSubmit={handleRequestCode} className="forgot-form">
                <div className="forgot-input-group">
                  <label className="forgot-label">Registered Email Address</label>
                  <div className="forgot-input-wrapper">
                    <Mail size={18} className="forgot-input-icon" />
                    <input
                      type="email"
                      placeholder="e.g. customer@swiftdrop.lk"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      required
                      className="forgot-input"
                    />
                  </div>
                </div>

                <button 
                  type="submit" 
                  disabled={loading}
                  className="btn-forgot-primary"
                >
                  {loading ? (
                    <span>Generating Code...</span>
                  ) : (
                    <span>Send Verification Code</span>
                  )}
                </button>
              </form>

              <div className="forgot-footer">
                <span>Remember your password? </span>
                <Link to="/login" className="forgot-login-link">Sign In</Link>
                <span style={{ margin: '0 8px', color: '#CBD5E1' }}>•</span>
                <Link to="/signup" className="forgot-login-link">Create Account</Link>
              </div>
            </div>
          )}

          {/* STEP 2: Enter Verification Code & New Password */}
          {step === 2 && (
            <div>
              <div className="forgot-icon-bubble">
                <ShieldCheck size={28} color="#16A34A" />
              </div>
              <h1 className="forgot-title">Reset Your Password</h1>
              <p className="forgot-subtitle">
                We sent a 6-digit verification code to <strong>{email}</strong>. Please check your inbox and enter the code below.
              </p>

              {/* Error Alert */}
              <AnimatePresence>
                {errorMessage && (
                  <motion.div 
                    className="forgot-alert-error"
                    initial={{ opacity: 0, y: -6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -6 }}
                  >
                    <AlertCircle size={17} color="#DC2626" />
                    <span>{errorMessage}</span>
                  </motion.div>
                )}
              </AnimatePresence>

              <form onSubmit={handleResetPassword} className="forgot-form">
                <div className="forgot-input-group">
                  <label className="forgot-label">6-Digit Verification Code</label>
                  <div className="forgot-input-wrapper">
                    <KeyRound size={18} className="forgot-input-icon" />
                    <input
                      type="text"
                      maxLength={6}
                      placeholder="e.g. 123456"
                      value={resetCode}
                      onChange={(e) => setResetCode(e.target.value)}
                      required
                      className="forgot-input code-input"
                    />
                  </div>
                </div>

                <div className="forgot-input-group">
                  <label className="forgot-label">New Password</label>
                  <div className="forgot-input-wrapper">
                    <Lock size={18} className="forgot-input-icon" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      placeholder="At least 6 characters"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      required
                      minLength={6}
                      className="forgot-input"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="forgot-eye-btn"
                    >
                      {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                    </button>
                  </div>
                </div>

                <div className="forgot-input-group">
                  <label className="forgot-label">Confirm New Password</label>
                  <div className="forgot-input-wrapper">
                    <Lock size={18} className="forgot-input-icon" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      placeholder="Re-enter new password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      required
                      className="forgot-input"
                    />
                  </div>
                </div>

                <button 
                  type="submit" 
                  disabled={loading}
                  className="btn-forgot-primary"
                >
                  {loading ? (
                    <span>Updating Password...</span>
                  ) : (
                    <span>Confirm & Reset Password</span>
                  )}
                </button>
              </form>

              <div className="forgot-step2-actions">
                <button 
                  type="button" 
                  onClick={() => { setStep(1); setErrorMessage(''); }}
                  className="btn-resend-back"
                >
                  <RefreshCw size={14} />
                  <span>Resend code or change email</span>
                </button>
              </div>
            </div>
          )}

          {/* STEP 3: Success Screen */}
          {step === 3 && (
            <motion.div 
              className="forgot-success-view"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.3 }}
            >
              <div className="forgot-success-icon-bubble">
                <CheckCircle2 size={40} color="#16A34A" />
              </div>
              <h1 className="forgot-title">Password Reset Complete!</h1>
              <p className="forgot-subtitle">
                Your password has been successfully updated. All previous sessions have been secured, and you can now sign in with your new password.
              </p>

              <button 
                onClick={() => navigate('/login')}
                className="btn-forgot-primary"
                style={{ marginTop: 24 }}
              >
                Proceed to Sign In
              </button>
            </motion.div>
          )}
        </motion.div>
      </main>
    </div>
  );
}

