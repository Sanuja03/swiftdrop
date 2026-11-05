import { Mail, Phone, User } from 'lucide-react'
import Alert from '@/shared/components/ui/Alert'
import Card, { CardHeader } from '@/shared/components/ui/Card'
import FormField from '@/shared/components/ui/FormField'
import { Input } from '@/shared/components/ui/Input'
import PageHeader from '@/shared/components/ui/PageHeader'
import { CURRENT_CUSTOMER } from '@/shared/data/currentCustomer'
import styles from './SettingsPage.module.css'

const FIELDS = [
  { label: 'Full name', icon: User, value: CURRENT_CUSTOMER.fullName },
  { label: 'Email', icon: Mail, value: CURRENT_CUSTOMER.email },
  { label: 'Phone', icon: Phone, value: CURRENT_CUSTOMER.phone },
]

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" subtitle="Your profile and contact details." />
      <Card className={styles.card}>
        <CardHeader title="Profile" icon={User} />
        <div className={styles.fields}>
          {FIELDS.map(({ label, icon, value }) => (
            <FormField key={label} label={label} icon={icon}>
              {(id) => <Input id={id} value={value} readOnly disabled />}
            </FormField>
          ))}
        </div>
        <Alert tone="info" title="Editing your profile comes with sign-in">
          You will be able to update these details once account sign-in is available.
        </Alert>
      </Card>
    </>
  )
}
