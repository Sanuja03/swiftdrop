import { useState, useEffect } from 'react'
import { CheckCircle2, Edit2, Mail, Phone, Save, ShieldCheck, User, X } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import Alert from '@/shared/components/ui/Alert'
import Button from '@/shared/components/ui/Button'
import Card, { CardHeader } from '@/shared/components/ui/Card'
import FormField from '@/shared/components/ui/FormField'
import { Input } from '@/shared/components/ui/Input'
import PageHeader from '@/shared/components/ui/PageHeader'
import { updateUserProfile } from '@/services/api'
import styles from './SettingsPage.module.css'

export default function SettingsPage() {
  const { user, token, updateUserSession } = useAuth()

  const [isEditing, setIsEditing] = useState(false)
  const [fullName, setFullName] = useState(user?.fullName || '')
  const [phone, setPhone] = useState(user?.phone || '')
  const [saving, setSaving] = useState(false)
  const [feedback, setFeedback] = useState(null)

  // Keep form fields synced if auth user state updates
  useEffect(() => {
    if (user) {
      setFullName(user.fullName || '')
      setPhone(user.phone || '')
    }
  }, [user])

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

  const formatRole = (role) => {
    if (!role) return 'Customer'
    return role.replace('ROLE_', '').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())
  }

  return (
    <>
      <PageHeader title="Settings" subtitle="Manage your personal profile, contact information, and account details." />

      {feedback && (
        <div className={styles.alertWrapper}>
          <Alert tone={feedback.tone} title={feedback.title}>
            {feedback.message}
          </Alert>
        </div>
      )}

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
      </Card>
    </>
  )
}
