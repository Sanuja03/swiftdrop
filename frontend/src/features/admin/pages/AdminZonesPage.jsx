import { MapPinned, Pencil, Plus } from 'lucide-react'
import { useEffect, useState } from 'react'
import Alert from '@/shared/components/ui/Alert'
import Button from '@/shared/components/ui/Button'
import Card, { CardHeader } from '@/shared/components/ui/Card'
import EmptyState from '@/shared/components/ui/EmptyState'
import FormField from '@/shared/components/ui/FormField'
import { Input } from '@/shared/components/ui/Input'
import PageHeader from '@/shared/components/ui/PageHeader'
import Spinner from '@/shared/components/ui/Spinner'
import { formatMoney } from '@/shared/utils/format'
import { createZone, getAllZones, setZoneActive, updateZone } from '../api/adminApi'
import styles from './Admin.module.css'

const EMPTY_FORM = { code: '', name: '', surcharge: '' }

function messageOf(error, fallback) {
  return error?.status && error.message ? error.message : fallback
}

function sortByName(zones) {
  return [...zones].sort((a, b) => a.name.localeCompare(b.name))
}

function CreateZoneForm({ onCreated, onError }) {
  const [form, setForm] = useState(EMPTY_FORM)
  const [isSaving, setIsSaving] = useState(false)
  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }))

  async function handleSubmit(event) {
    event.preventDefault()
    setIsSaving(true)
    try {
      const zone = await createZone({ code: form.code.trim(), name: form.name.trim(), surcharge: Number(form.surcharge) })
      setForm(EMPTY_FORM)
      onCreated(zone)
    } catch (error) {
      onError(messageOf(error, 'Could not create the zone. Please try again.'))
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <Card as="form" onSubmit={handleSubmit}>
      <CardHeader title="Add a zone" icon={Plus} />
      <div className={styles.createForm}>
        <FormField label="Code" hint="Letters, digits, _ (permanent)">
          {(id) => <Input id={id} value={form.code} onChange={update('code')} placeholder="ZONE_CODE" pattern="[A-Za-z0-9_]{2,40}" required />}
        </FormField>
        <FormField label="Name">
          {(id) => <Input id={id} value={form.name} onChange={update('name')} placeholder="Zone name" maxLength={100} required />}
        </FormField>
        <FormField label="Surcharge (LKR)">
          {(id) => <Input id={id} type="number" min="0" step="0.01" inputMode="decimal" value={form.surcharge} onChange={update('surcharge')} placeholder="0" required />}
        </FormField>
        <Button type="submit" disabled={isSaving}>{isSaving ? 'Adding…' : 'Add zone'}</Button>
      </div>
    </Card>
  )
}

function ZoneRow({ zone, onSaved, onError }) {
  const [draft, setDraft] = useState(null)
  const [isBusy, setIsBusy] = useState(false)

  async function run(action, fallback) {
    setIsBusy(true)
    try {
      onSaved(await action())
      setDraft(null)
    } catch (error) {
      onError(messageOf(error, fallback))
    } finally {
      setIsBusy(false)
    }
  }

  const save = () => run(
    () => updateZone(zone.id, { name: draft.name.trim(), surcharge: Number(draft.surcharge) }),
    `Could not save ${zone.code}. Please try again.`,
  )
  const toggle = () => run(
    () => setZoneActive(zone.id, !zone.active),
    `Could not ${zone.active ? 'deactivate' : 'activate'} ${zone.code}. Please try again.`,
  )
  const draftValid = draft && draft.name.trim() && draft.surcharge !== '' && Number(draft.surcharge) >= 0

  return (
    <tr className={zone.active ? '' : styles.inactiveRow}>
      <td className={styles.code}>{zone.code}</td>
      <td>
        {draft ? (
          <Input aria-label={`Name of ${zone.code}`} className={styles.inlineInput} value={draft.name} maxLength={100}
            onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
        ) : zone.name}
      </td>
      <td className={styles.number}>
        {draft ? (
          <Input aria-label={`Surcharge of ${zone.code}`} className={styles.inlineInput} type="number" min="0" step="0.01"
            value={draft.surcharge} onChange={(event) => setDraft({ ...draft, surcharge: event.target.value })} />
        ) : formatMoney(zone.surcharge)}
      </td>
      <td>
        <span className={`${styles.pill} ${zone.active ? styles.on : styles.off}`}>{zone.active ? 'Active' : 'Inactive'}</span>
      </td>
      <td>
        <div className={styles.rowActions}>
          {draft ? (
            <>
              <Button size="sm" onClick={save} disabled={isBusy || !draftValid}>{isBusy ? 'Saving…' : 'Save'}</Button>
              <Button size="sm" variant="ghost" onClick={() => setDraft(null)} disabled={isBusy}>Cancel</Button>
            </>
          ) : (
            <>
              <Button size="sm" variant="ghost" icon={Pencil} onClick={() => setDraft({ name: zone.name, surcharge: String(zone.surcharge) })} disabled={isBusy}>
                Edit
              </Button>
              <Button size="sm" variant="secondary" onClick={toggle} disabled={isBusy}>
                {zone.active ? 'Deactivate' : 'Activate'}
              </Button>
            </>
          )}
        </div>
      </td>
    </tr>
  )
}

/** Admin: delivery zones. Zones are never deleted, only deactivated, because parcels refer to them. */
export default function AdminZonesPage() {
  const [zones, setZones] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    getAllZones({ signal: controller.signal })
      .then((items) => setZones(sortByName(items)))
      .catch((caught) => {
        if (!controller.signal.aborted) setError(messageOf(caught, 'Could not load zones. Please try again.'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false)
      })
    return () => controller.abort()
  }, [])

  function showError(message) {
    setNotice('')
    setError(message)
  }

  function upsert(zone, message) {
    setZones((current) => sortByName([...current.filter(({ id }) => id !== zone.id), zone]))
    setError('')
    setNotice(message)
  }

  return (
    <div className={styles.stack}>
      <PageHeader title="Zones" subtitle="Delivery zones customers can pick. Changes apply to new estimates straight away." />
      {error && <Alert tone="error" title="Something went wrong">{error}</Alert>}
      {notice && <Alert tone="info" title={notice} />}
      <CreateZoneForm onCreated={(zone) => upsert(zone, `Zone ${zone.code} added.`)} onError={showError} />
      <Card>
        <CardHeader title="All zones" icon={MapPinned} />
        {isLoading && <Spinner label="Loading zones…" />}
        {!isLoading && zones.length === 0 && !error && (
          <EmptyState icon={MapPinned} title="No zones yet">Add the first zone above.</EmptyState>
        )}
        {zones.length > 0 && (
          <div className={styles.scroller}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">Code</th>
                  <th scope="col">Name</th>
                  <th scope="col">Surcharge</th>
                  <th scope="col">Status</th>
                  <th scope="col"><span className="visually-hidden">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {zones.map((zone) => (
                  <ZoneRow key={zone.id} zone={zone} onSaved={(saved) => upsert(saved, `Zone ${saved.code} saved.`)} onError={showError} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
