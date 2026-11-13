import { ArrowLeft, ArrowRight, Info, MapPin, Package, Scale, Tag } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { SERVICE_ICONS } from '@/shared/components/brand/serviceIcons'
import Alert from '@/shared/components/ui/Alert'
import Button from '@/shared/components/ui/Button'
import Card, { CardHeader } from '@/shared/components/ui/Card'
import FormField from '@/shared/components/ui/FormField'
import { Input, Select } from '@/shared/components/ui/Input'
import PageHeader from '@/shared/components/ui/PageHeader'
import Spinner from '@/shared/components/ui/Spinner'
import { ROUTES } from '@/shared/config/routes'
import { findServiceOption, SERVICE_OPTIONS } from '@/shared/domain/serviceOptions'
import { formatMoney } from '@/shared/utils/format'
import { createParcel, estimateFee, getZones } from '../api/bookingApi'
import styles from './CreateDeliveryPage.module.css'

const STEPS = ['Details', 'Review', 'Confirm']
const PARCEL_TYPES = [
  { value: 'DOCUMENTS', label: 'Documents (up to 0.5 kg)', min: 0, max: 0.5 },
  { value: 'SMALL', label: 'Small (up to 2 kg)', min: 0, max: 2 },
  { value: 'MEDIUM', label: 'Medium (2-10 kg)', min: 2, max: 10 },
  { value: 'LARGE', label: 'Large (10-30 kg)', min: 10, max: 30 },
]
const INITIAL_FORM = {
  pickupAddress: '',
  dropoffAddress: '',
  pickupZoneId: '',
  dropoffZoneId: '',
  parcelSize: PARCEL_TYPES[1].value,
  weightKg: '',
  serviceType: SERVICE_OPTIONS[0].value,
}

function Stepper({ current }) {
  return (
    <ol className={styles.steps}>
      {STEPS.map((label, index) => (
        <li key={label} className={index <= current ? styles.stepActive : ''} aria-current={index === current ? 'step' : undefined}>
          <span className={styles.stepNumber}>{index + 1}</span>
          {label}
        </li>
      ))}
    </ol>
  )
}

function DetailsStep({ form, update, zones, weightError }) {
  return (
    <div className={styles.fields}>
      <div className={styles.addressRow}>
        <FormField label="Pickup Address" icon={MapPin}>
          {(id) => <Input id={id} value={form.pickupAddress} onChange={update('pickupAddress')} placeholder="Enter pickup address" required />}
        </FormField>
        <FormField label="Pickup Zone" icon={MapPin}>
          {(id) => <Select id={id} value={form.pickupZoneId} onChange={update('pickupZoneId')} required><option value="">Select zone</option>{zones.map(({ id: zoneId, name }) => <option key={zoneId} value={zoneId}>{name}</option>)}</Select>}
        </FormField>
      </div>
      <div className={styles.addressRow}>
        <FormField label="Drop-off Address" icon={MapPin}>
          {(id) => <Input id={id} value={form.dropoffAddress} onChange={update('dropoffAddress')} placeholder="Enter drop-off address" required />}
        </FormField>
        <FormField label="Drop-off Zone" icon={MapPin}>
          {(id) => <Select id={id} value={form.dropoffZoneId} onChange={update('dropoffZoneId')} required><option value="">Select zone</option>{zones.map(({ id: zoneId, name }) => <option key={zoneId} value={zoneId}>{name}</option>)}</Select>}
        </FormField>
      </div>
      <div className={styles.row}>
        <FormField label="Parcel Details" icon={Package}>
          {(id) => <Select id={id} value={form.parcelSize} onChange={update('parcelSize')}>{PARCEL_TYPES.map(({ value, label }) => <option key={value} value={value}>{label}</option>)}</Select>}
        </FormField>
        <FormField label="Weight (kg)" icon={Scale}>
          {(id) => <Input id={id} type="number" min="0.1" step="0.1" inputMode="decimal" value={form.weightKg} onChange={update('weightKg')} placeholder="5.0" aria-invalid={Boolean(weightError)} aria-describedby={weightError ? 'weight-error' : undefined} required />}
        </FormField>
      </div>
      {weightError && <p id="weight-error" className={styles.fieldError} role="alert">{weightError}</p>}
      <fieldset className={styles.options}>
        <legend className={styles.legend}>
          <Tag size={15} aria-hidden="true" /> Delivery Option
        </legend>
        <div className={styles.optionGrid}>
          {SERVICE_OPTIONS.map(({ value, name, description, fromPrice }) => {
            const Icon = SERVICE_ICONS[value]
            const checked = form.serviceType === value
            return (
              <label key={value} className={`${styles.option} ${checked ? styles.optionChecked : ''}`}>
                <input type="radio" name="serviceType" value={value} checked={checked} onChange={update('serviceType')} className="visually-hidden" />
                <Icon size={20} className={styles.optionIcon} aria-hidden="true" />
                <span className={styles.optionName}>{name}</span>
                <span className={styles.optionText}>{description}</span>
                <span className={styles.optionPrice}>{formatMoney(fromPrice)}</span>
              </label>
            )
          })}
        </div>
      </fieldset>
    </div>
  )
}

function ReviewStep({ form, zones }) {
  const size = PARCEL_TYPES.find(({ value }) => value === form.parcelSize)
  const rows = [
    ['Pickup', form.pickupAddress],
    ['Pickup zone', zones.find(({ id }) => id === form.pickupZoneId)?.name],
    ['Drop-off', form.dropoffAddress],
    ['Drop-off zone', zones.find(({ id }) => id === form.dropoffZoneId)?.name],
    ['Parcel size', size?.label],
    ['Weight', `${form.weightKg} kg`],
    ['Service', findServiceOption(form.serviceType).name],
  ]
  return (
    <dl className={styles.review}>
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  )
}

function PriceEstimate({ form, fee, isLoading }) {
  const option = findServiceOption(form.serviceType)
  return (
    <Card className={styles.estimate}>
      <CardHeader title="Price Estimate" icon={Tag} />
      {isLoading ? <Spinner label="Updating estimate…" /> : fee ? <dl className={styles.estimateRows}>
        <div>
          <dt>Delivery Option</dt>
          <dd>{option.name}</dd>
        </div>
        <div>
          <dt>Base fee</dt>
          <dd>{formatMoney(fee.baseFee, fee.currency)}</dd>
        </div>
        <div>
          <dt>Weight charge</dt>
          <dd>{formatMoney(fee.weightCharge, fee.currency)}</dd>
        </div>
        <div><dt>Zone charge</dt><dd>{formatMoney(fee.zoneCharge, fee.currency)}</dd></div>
      </dl>
      : <p className={styles.note}>Select zones and enter a valid parcel weight to see your estimate.</p>}
      {fee && !isLoading && <div className={styles.total}>
        <span>Estimated Total</span>
        <strong>{formatMoney(fee.totalFee, fee.currency)}</strong>
      </div>}
      <p className={styles.note}>
        <Info size={15} aria-hidden="true" /> Estimated delivery fee based on your parcel and selected zones.
      </p>
    </Card>
  )
}

/** Customer booking flow backed by the booking API module. */
export default function CreateDeliveryPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState(0)
  const [form, setForm] = useState(INITIAL_FORM)
  const [zones, setZones] = useState([])
  const [fee, setFee] = useState(null)
  const [isEstimating, setIsEstimating] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    getZones().then((items) => { if (active) setZones(items) }).catch(() => { if (active) setError('Could not load delivery zones. Please try again.') })
    return () => { active = false }
  }, [])

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }))
  const size = PARCEL_TYPES.find(({ value }) => value === form.parcelSize)
  const weight = Number(form.weightKg)
  const weightValid = Number.isFinite(weight) && weight > 0 && weight >= size.min && weight <= size.max
  const weightError = form.weightKg && !weightValid
    ? size.min === 0
      ? `Weight must be above 0 and no more than ${size.max} kg.`
      : `Weight must be between ${size.min} and ${size.max} kg.`
    : ''
  const detailsComplete = form.pickupAddress.trim() && form.dropoffAddress.trim() && form.pickupZoneId && form.dropoffZoneId && weightValid && fee && !isEstimating

  useEffect(() => {
    if (!form.pickupZoneId || !form.dropoffZoneId || !weightValid) {
      setFee(null)
      setIsEstimating(false)
      return undefined
    }
    let active = true
    setFee(null)
    setIsEstimating(true)
    const timer = setTimeout(() => {
      estimateFee({ pickupZoneId: form.pickupZoneId, dropoffZoneId: form.dropoffZoneId, parcelSize: form.parcelSize, weightKg: weight, serviceType: form.serviceType })
        .then((result) => { if (active) setFee(result) })
        .catch(() => { if (active) setError('Could not estimate the delivery fee. Please try again.') })
        .finally(() => { if (active) setIsEstimating(false) })
    }, 300)
    return () => { active = false; clearTimeout(timer) }
  }, [form.pickupZoneId, form.dropoffZoneId, form.parcelSize, form.weightKg, form.serviceType, weightValid, weight])

  function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setStep((current) => Math.min(current + 1, STEPS.length - 1))
  }

  async function handleConfirm() {
    setIsCreating(true)
    setError('')
    const request = { pickupZoneId: form.pickupZoneId, dropoffZoneId: form.dropoffZoneId, pickupZoneName: zones.find(({ id }) => id === form.pickupZoneId)?.name, dropoffZoneName: zones.find(({ id }) => id === form.dropoffZoneId)?.name, parcelSize: form.parcelSize, weightKg: weight, serviceType: form.serviceType, pickupAddress: form.pickupAddress, dropoffAddress: form.dropoffAddress }
    try {
      const parcel = await createParcel(request)
      const booking = { parcel, request, fee }
      try {
        sessionStorage.setItem(`booking:${parcel.trackingNumber}`, JSON.stringify(booking))
      } catch {
        // Navigation state still carries the booking details for this session.
      }
      navigate(ROUTES.bookingConfirmation(parcel.trackingNumber), { state: booking })
    } catch {
      setError('Could not create your booking. Please try again.')
      setIsCreating(false)
    }
  }

  return (
    <>
      <PageHeader title="Create a Delivery" subtitle="Book a parcel pickup in a few simple steps." />
      <div className={styles.layout}>
        <Card as="form" onSubmit={handleSubmit} className={styles.formCard}>
          <Stepper current={step} />
          {error && <Alert tone="error" title="Booking issue">{error}</Alert>}
          {step === 0 && <DetailsStep form={form} update={update} zones={zones} weightError={weightError} />}
          {step >= 1 && <ReviewStep form={form} zones={zones} />}
          {step === 2 && <p className={styles.confirmText}>Confirm this delivery to create your parcel booking.</p>}
          <div className={styles.actions}>
            {step > 0 && (
              <Button variant="secondary" icon={ArrowLeft} onClick={() => setStep(step - 1)}>
                Back
              </Button>
            )}
            {step === 0 && <Button type="submit" iconAfter={ArrowRight} disabled={!detailsComplete}>Continue</Button>}
            {step === 1 && <Button type="submit" iconAfter={ArrowRight} disabled={!fee || isEstimating}>Continue to confirm</Button>}
            {step === 2 && <Button type="button" iconAfter={ArrowRight} onClick={handleConfirm} disabled={isCreating}>{isCreating ? 'Creating booking…' : 'Confirm booking'}</Button>}
          </div>
        </Card>
        <PriceEstimate form={form} fee={fee} isLoading={isEstimating} />
      </div>
    </>
  )
}
