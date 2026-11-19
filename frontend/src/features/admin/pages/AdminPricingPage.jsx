import { Tags } from 'lucide-react'
import { useEffect, useState } from 'react'
import { SERVICE_ICONS } from '@/shared/components/brand/serviceIcons'
import Alert from '@/shared/components/ui/Alert'
import Button from '@/shared/components/ui/Button'
import Card, { CardHeader } from '@/shared/components/ui/Card'
import EmptyState from '@/shared/components/ui/EmptyState'
import FormField from '@/shared/components/ui/FormField'
import { Input } from '@/shared/components/ui/Input'
import PageHeader from '@/shared/components/ui/PageHeader'
import Spinner from '@/shared/components/ui/Spinner'
import { findServiceOption } from '@/shared/domain/serviceOptions'
import { formatDayTime } from '@/shared/utils/format'
import { getPricingRules, updatePricingRule } from '../api/adminApi'
import styles from './Admin.module.css'

const FEE_FIELDS = [
  { key: 'baseFee', label: 'Base fee', hint: 'Covers the first 1 kg' },
  { key: 'perKgFee', label: 'Per kg fee', hint: 'For each kg above the first' },
  { key: 'interZoneFee', label: 'Inter-zone fee', hint: 'When pickup and drop-off zones differ' },
]

function messageOf(error, fallback) {
  return error?.status && error.message ? error.message : fallback
}

function toDraft(rule) {
  return {
    baseFee: String(rule.baseFee),
    perKgFee: String(rule.perKgFee),
    interZoneFee: String(rule.interZoneFee),
    active: rule.active,
  }
}

function RuleCard({ rule, onSaved }) {
  const [draft, setDraft] = useState(() => toDraft(rule))
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const service = findServiceOption(rule.serviceType)
  const Icon = SERVICE_ICONS[rule.serviceType] ?? Tags

  const original = toDraft(rule)
  const isDirty = FEE_FIELDS.some(({ key }) => draft[key] !== original[key]) || draft.active !== original.active
  const isValid = FEE_FIELDS.every(({ key }) => draft[key] !== '' && Number(draft[key]) >= 0)

  function change(key, value) {
    setDraft((current) => ({ ...current, [key]: value }))
    setSaved(false)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setIsSaving(true)
    setError('')
    try {
      const updated = await updatePricingRule(rule.serviceType, {
        baseFee: Number(draft.baseFee),
        perKgFee: Number(draft.perKgFee),
        interZoneFee: Number(draft.interZoneFee),
        active: draft.active,
      })
      onSaved(updated)
      setDraft(toDraft(updated))
      setSaved(true)
    } catch (caught) {
      setError(messageOf(caught, `Could not save ${service.name}. Please try again.`))
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <Card as="form" onSubmit={handleSubmit} className={styles.ruleCard}>
      <CardHeader title={service.name} icon={Icon} />
      {error && <Alert tone="error" title="Not saved">{error}</Alert>}
      {saved && !isDirty && <Alert tone="info" title={`${service.name} saved`}>New estimates use these prices now.</Alert>}
      <div className={styles.ruleFields}>
        {FEE_FIELDS.map(({ key, label, hint }) => (
          <FormField key={key} label={`${label} (${rule.currency})`} hint={hint}>
            {(id) => (
              <Input id={id} type="number" min="0" step="0.01" inputMode="decimal" value={draft[key]}
                onChange={(event) => change(key, event.target.value)} placeholder="0" required />
            )}
          </FormField>
        ))}
      </div>
      <label className={styles.toggle}>
        <input type="checkbox" checked={draft.active} onChange={(event) => change('active', event.target.checked)} />
        Customers can book this service
      </label>
      <div className={styles.ruleFooter}>
        <span className={styles.hint}>{rule.updatedAt ? `Updated ${formatDayTime(rule.updatedAt)}` : ''}</span>
        <Button type="submit" size="sm" disabled={isSaving || !isDirty || !isValid}>{isSaving ? 'Saving…' : 'Save'}</Button>
      </div>
    </Card>
  )
}

/** Admin: the fees behind every estimate, one rule per delivery service. */
export default function AdminPricingPage() {
  const [rules, setRules] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    getPricingRules({ signal: controller.signal })
      .then(setRules)
      .catch((caught) => {
        if (!controller.signal.aborted) setError(messageOf(caught, 'Could not load pricing rules. Please try again.'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false)
      })
    return () => controller.abort()
  }, [])

  const replace = (updated) =>
    setRules((current) => current.map((rule) => (rule.serviceType === updated.serviceType ? updated : rule)))

  return (
    <div className={styles.stack}>
      <PageHeader
        title="Pricing"
        subtitle="Fee = base fee + per kg fee for each kg above 1 kg + inter-zone fee (when zones differ) + drop-off zone surcharge. At least one service must stay bookable."
      />
      {error && <Alert tone="error" title="Something went wrong">{error}</Alert>}
      {isLoading && <Card><Spinner label="Loading pricing rules…" /></Card>}
      {!isLoading && rules.length === 0 && !error && (
        <Card><EmptyState icon={Tags} title="No pricing rules">Pricing rules come from the database setup script.</EmptyState></Card>
      )}
      <div className={styles.ruleGrid}>
        {rules.map((rule) => <RuleCard key={rule.serviceType} rule={rule} onSaved={replace} />)}
      </div>
    </div>
  )
}
