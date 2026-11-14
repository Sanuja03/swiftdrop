import { Mail, Phone, User } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import Alert from '@/shared/components/ui/Alert'
import Button from '@/shared/components/ui/Button'
import Card, { CardHeader } from '@/shared/components/ui/Card'
import FormField from '@/shared/components/ui/FormField'
import { Input } from '@/shared/components/ui/Input'
import PageHeader from '@/shared/components/ui/PageHeader'
import styles from './SettingsPage.module.css'

export default function SettingsPage() {
  const { user } = useAuth()
  const fields = [
    { label: 'Full name', icon: User, value: user?.fullName },
    { label: 'Email', icon: Mail, value: user?.email },
    { label: 'Phone', icon: Phone, value: user?.phone },
  ]

  return (
    <>
      <PageHeader title="Settings" subtitle="Your profile and contact details." />
      <Card className={styles.card}>
        <CardHeader title="Profile" icon={User} />
        <div className={styles.fields}>
          {fields.map(({ label, icon, value }) => (
            <FormField key={label} label={label} icon={icon}>
              {(id) => <Input id={id} value={value ?? ''} placeholder="Not set" readOnly disabled />}
            </FormField>
          ))}
        </div>
        <Alert
          tone="info"
          title="Need to change something?"
          action={
            <Button variant="secondary" size="sm" to="/profile">
              Edit profile
            </Button>
          }
        >
          You can update your name and phone number on your profile page.
        </Alert>
      </Card>
    </>
  )
}
