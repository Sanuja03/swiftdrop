import { useState } from 'react'
import { Edit2, KeyRound, Lock, Mail, Phone, Save, ShieldCheck, User, X } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import Alert from '@/shared/components/ui/Alert'
import Button from '@/shared/components/ui/Button'
import Card, { CardHeader } from '@/shared/components/ui/Card'
import FormField from '@/shared/components/ui/FormField'
import { Input } from '@/shared/components/ui/Input'
import PageHeader from '@/shared/components/ui/PageHeader'
import { changeUserPassword, updateUserProfile } from '@/services/api'
import styles from './SettingsPage.module.css'

export default function SettingsPage() {
  const { user, token, updateUserSession } = useAuth()

  // Profile edit state
  const [isEditing, setIsEditing] = useState(false)
  const [fullName, setFullName] = useState(user?.fullName || '')
  const [phone, setPhone] = useState(user?.phone || '')
  const [saving, setSaving] = useState(false)
  const [feedback, setFeedback] = useState(null)

  // Password change state (for email/password users only)
  const [showPasswordForm, setShowPasswordForm] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordSaving, setPasswordSaving] = useState(false)
  const [passwordFeedback, setPasswordFeedback] = useState(null)

  const isLocalUser = !user?.provider || user.provider === 'LOCAL'

  const handleStartEdit = () => {
    setFullName(user?.fullName || '')
    setPhone(user?.phone || '')
    setFeedback(null)
    setIsEditing(true)
  }

  const handleCancel = () => {
    setFullName(user?.fullName || '')
    setPhone(user?.phone || '')
    setFeedback(null)
    setIsEditing(false)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setFeedback(null)

    const trimmedName = fullName.trim()
    const trimmedPhone = phone.trim()

    if (!trimmedName) {
      setFeedback({ tone: 'error', title: 'Name required', message: 'Please enter your full name.' })
      return
    }

    if (!trimmedPhone) {
      setFeedback({ tone: 'error', title: 'Phone required', message: 'Please enter your contact mobile number.' })
      return
    }

    setSaving(true)
    const res = await updateUserProfile({ fullName: trimmedName, phone: trimmedPhone }, token)
    setSaving(false)

    if (res.success && res.data) {
      updateUserSession(res.data)
      setFeedback({ tone: 'info', title: 'Saved', message: 'Your profile details have been updated successfully!' })
      setIsEditing(false)
      setTimeout(() => setFeedback(null), 4000)
    } else {
      setFeedback({ tone: 'error', title: 'Update failed', message: res.error || 'Failed to update profile. Please try again.' })
    }
  }

  const handlePasswordSubmit = async (e) => {
    e.preventDefault()
    setPasswordFeedback(null)

    if (!currentPassword) {
      setPasswordFeedback({ tone: 'error', title: 'Current password required', message: 'Please enter your current password.' })
      return
    }

    if (!newPassword || newPassword.length < 6) {
      setPasswordFeedback({ tone: 'error', title: 'Invalid new password', message: 'New password must be at least 6 characters long.' })
      return
    }

    if (newPassword !== confirmPassword) {
      setPasswordFeedback({ tone: 'error', title: 'Passwords do not match', message: 'New password and confirmation password do not match.' })
      return
    }

    if (currentPassword === newPassword) {
      setPasswordFeedback({ tone: 'error', title: 'Password unchanged', message: 'New password cannot be the same as your current password.' })
      return
    }

    setPasswordSaving(true)
    const res = await changeUserPassword(currentPassword, newPassword, confirmPassword)
    setPasswordSaving(false)

    if (res.success) {
      setPasswordFeedback({
        tone: 'info',
        title: 'Password updated',
        message: res.data?.message || 'Your password has been changed successfully!'
      })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setShowPasswordForm(false)
      setTimeout(() => setPasswordFeedback(null), 5000)
    } else {
      setPasswordFeedback({
        tone: 'error',
        title: 'Change password failed',
        message: res.error || 'Failed to change password. Please verify your current password.'
      })
    }
  }

  const handleCancelPassword = () => {
    setShowPasswordForm(false)
    setCurrentPassword('')
    setNewPassword('')
    setConfirmPassword('')
    setPasswordFeedback(null)
  }

  const formatRole = (role) => {
    if (!role) return 'Customer'
    return role.replace('ROLE_', '').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())
  }

  return (
    <>
      <PageHeader title="Settings" subtitle="Manage your personal profile, contact information, and account security." />

      {feedback && (
        <div className={styles.alertWrapper}>
          <Alert tone={feedback.tone} title={feedback.title}>
            {feedback.message}
          </Alert>
        </div>
      )}

      {/* Profile Details Card */}
      <Card className={styles.card}>
        <CardHeader
          title="Profile Details"
          icon={User}
          action={
            !isEditing && (
              <Button variant="secondary" size="sm" icon={Edit2} onClick={handleStartEdit}>
                Edit profile
              </Button>
            )
          }
        />

        {isEditing ? (
          <form onSubmit={handleSubmit} className={styles.form}>
            <div className={styles.fields}>
              <FormField label="Full name" icon={User}>
                {(id) => (
                  <Input
                    id={id}
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    placeholder="e.g. Kasun Perera"
                    required
                    autoFocus
                  />
                )}
              </FormField>

              <FormField
                label="Mobile phone"
                icon={Phone}
                hint="Used for real-time delivery SMS alerts, pickup coordination, and driver calls."
              >
                {(id) => (
                  <Input
                    id={id}
                    type="tel"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="e.g. 077 123 4567"
                    required
                  />
                )}
              </FormField>

              <FormField
                label="Email address"
                icon={Mail}
                hint="Your registered email address is your login identifier and cannot be changed."
              >
                {(id) => (
                  <Input id={id} value={user?.email || ''} readOnly disabled />
                )}
              </FormField>
            </div>

            <div className={styles.actions}>
              <Button type="submit" variant="primary" icon={Save} disabled={saving}>
                {saving ? 'Saving...' : 'Save Changes'}
              </Button>
              <Button type="button" variant="secondary" icon={X} onClick={handleCancel} disabled={saving}>
                Cancel
              </Button>
            </div>
          </form>
        ) : (
          <>
            <div className={styles.fields}>
              <FormField label="Full name" icon={User}>
                {(id) => <Input id={id} value={user?.fullName || ''} placeholder="Not set" readOnly disabled />}
              </FormField>

              <FormField label="Email address" icon={Mail}>
                {(id) => <Input id={id} value={user?.email || ''} readOnly disabled />}
              </FormField>

              <FormField label="Mobile phone" icon={Phone}>
                {(id) => (
                  <Input
                    id={id}
                    value={user?.phone || ''}
                    placeholder="No phone number provided"
                    readOnly
                    disabled
                  />
                )}
              </FormField>
            </div>

            {!user?.phone ? (
              <Alert
                tone="info"
                title="Add your phone number"
                action={
                  <Button variant="secondary" size="sm" icon={Edit2} onClick={handleStartEdit}>
                    Add Phone
                  </Button>
                }
              >
                Adding your mobile number enables SMS delivery status updates and easy courier pickup.
              </Alert>
            ) : null}
          </>
        )}
      </Card>

      {/* Change Password Card - EXCLUSIVELY for Email & Password (Local) users */}
      {isLocalUser && (
        <Card className={styles.passwordCard}>
          <CardHeader
            title="Change Password"
            icon={KeyRound}
            action={
              !showPasswordForm && (
                <Button
                  variant="secondary"
                  size="sm"
                  icon={KeyRound}
                  onClick={() => {
                    setShowPasswordForm(true)
                    setPasswordFeedback(null)
                  }}
                >
                  Change password
                </Button>
              )
            }
          />

          {passwordFeedback && (
            <div className={styles.alertWrapper}>
              <Alert tone={passwordFeedback.tone} title={passwordFeedback.title}>
                {passwordFeedback.message}
              </Alert>
            </div>
          )}

          {showPasswordForm ? (
            <form onSubmit={handlePasswordSubmit} className={styles.form}>
              <div className={styles.fields}>
                <FormField label="Current password" icon={Lock}>
                  {(id) => (
                    <Input
                      id={id}
                      type="password"
                      value={currentPassword}
                      onChange={(e) => setCurrentPassword(e.target.value)}
                      placeholder="Enter your current password"
                      required
                      autoFocus
                    />
                  )}
                </FormField>

                <FormField
                  label="New password"
                  icon={Lock}
                  hint="Must be at least 6 characters long and different from your current password."
                >
                  {(id) => (
                    <Input
                      id={id}
                      type="password"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      placeholder="Enter new password (min. 6 characters)"
                      required
                    />
                  )}
                </FormField>

                <FormField label="Confirm new password" icon={Lock}>
                  {(id) => (
                    <Input
                      id={id}
                      type="password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="Re-type new password"
                      required
                    />
                  )}
                </FormField>
              </div>

              <div className={styles.actions}>
                <Button type="submit" variant="primary" icon={Save} disabled={passwordSaving}>
                  {passwordSaving ? 'Updating Password...' : 'Update Password'}
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  icon={X}
                  onClick={handleCancelPassword}
                  disabled={passwordSaving}
                >
                  Cancel
                </Button>
              </div>
            </form>
          ) : (
            <div className={styles.passwordSummaryRow}>
              <div>
                <p className={styles.passwordNoticeText}>
                  Your account is protected with a password.
                </p>
                <p className={styles.passwordNoticeSubtext}>
                  Regularly updating your password helps keep your deliveries and contact details secure.
                </p>
              </div>
            </div>
          )}
        </Card>
      )}

      {/* Account & Security Information */}
      <Card className={styles.accountMetaCard}>
        <CardHeader title="Account & Security" icon={ShieldCheck} />
        <div className={styles.metaGrid}>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>Account Role</span>
            <span className={styles.metaValue}>{formatRole(user?.role)}</span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>Account Status</span>
            <span className={styles.metaValue}>
              <span className={`${styles.statusPill} ${user?.status === 'ACTIVE' ? '' : styles.statusPillPending}`}>
                <span className={styles.statusDot} />
                {user?.status === 'ACTIVE' ? 'Active' : 'Pending'}
              </span>
            </span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>Sign-in Provider</span>
            <span className={styles.metaValue}>
              {user?.provider === 'GOOGLE' ? 'Google OAuth 2.0' : 'Email & Password'}
            </span>
          </div>
        </div>

        {user?.provider === 'GOOGLE' && (
          <div className={styles.googleNotice}>
            <ShieldCheck size={18} className={styles.googleNoticeIcon} />
            <div>
              <strong className={styles.googleNoticeTitle}>Google Account Authentication</strong>
              <p className={styles.googleNoticeText}>
                You are signed in with Google. Passwords and security credentials are directly managed through your Google account.
              </p>
            </div>
          </div>
        )}
      </Card>
    </>
  )
}
