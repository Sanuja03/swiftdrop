import { ArrowLeft, ArrowRight, CalendarClock, Info, MapPin, Package, Phone, Scale, Tag, User } from 'lucide-react'
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
import { formatFullDate, formatMoney } from '@/shared/utils/format'
import { createParcel, estimateFee, getZones } from '../api/bookingApi'
import { useServicePrices } from '../hooks/useServicePrices'
import styles from './CreateDeliveryPage.module.css'

const STEPS = ['Details', 'Review', 'Confirm']
const PARCEL_TYPES = [
  { value: 'DOCUMENTS', label: 'Documents (up to 0.5 kg)', min: 0, max: 0.5 },
  { value: 'SMALL', label: 'Small (up to 2 kg)', min: 0, max: 2 },
  { value: 'MEDIUM', label: 'Medium (2-10 kg)', min: 2, max: 10 },
  { value: 'LARGE', label: 'Large (10-30 kg)', min: 10, max: 30 },
]
/** Same rule as CreateParcelRequest.PHONE_PATTERN in the backend. */
const PHONE_PATTERN = /^\+?[0-9][0-9 -]{5,18}[0-9]$/
const INITIAL_FORM = {
  pickupAddress: '',
  dropoffAddress: '',
  pickupZoneId: '',
  dropoffZoneId: '',
  recipientName: '',
  recipientPhone: '',
  parcelSize: PARCEL_TYPES[1].value,
  weightKg: '',
  serviceType: SERVICE_OPTIONS[0].value,
}

/** The API's own explanation (RFC 9457 detail) when there is one. */
function apiMessage(error, fallback) {
  return error?.status && error.message ? error.message : fallback
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

function DetailsStep({ form, update, zones, weightError, phoneError, prices }) {
  return (
    <div className={styles.fields}>
      <div className={styles.addressRow}>
        <FormField label="Pickup Address" icon={MapPin}>
          {(id) => <Input id={id} value={form.pickupAddress} onChange={update('pickupAddress')} placeholder="Enter pickup address" maxLength={255} required />}
        </FormField>
        <FormField label="Pickup Zone" icon={MapPin}>
          {(id) => <Select id={id} value={form.pickupZoneId} onChange={update('pickupZoneId')} required><option value="">Select zone</option>{zones.map(({ id: zoneId, name }) => <option key={zoneId} value={zoneId}>{name}</option>)}</Select>}
        </FormField>
      </div>
      <div className={styles.addressRow}>
        <FormField label="Drop-off Address" icon={MapPin}>
          {(id) => <Input id={id} value={form.dropoffAddress} onChange={update('dropoffAddress')} placeholder="Enter drop-off address" maxLength={255} required />}
        </FormField>
        <FormField label="Drop-off Zone" icon={MapPin}>
          {(id) => <Select id={id} value={form.dropoffZoneId} onChange={update('dropoffZoneId')} required><option value="">Select zone</option>{zones.map(({ id: zoneId, name }) => <option key={zoneId} value={zoneId}>{name}</option>)}</Select>}
        </FormField>
      </div>
      <div className={styles.row}>
        <FormField label="Recipient Name" icon={User}>
          {(id) => <Input id={id} value={form.recipientName} onChange={update('recipientName')} placeholder="Recipient's full name" autoComplete="off" maxLength={120} required />}
        </FormField>
        <FormField label="Recipient Phone" icon={Phone}>
          {(id) => <Input id={id} type="tel" value={form.recipientPhone} onChange={update('recipientPhone')} placeholder="Phone number" autoComplete="off" inputMode="tel" aria-invalid={Boolean(phoneError)} aria-describedby={phoneError ? 'phone-error' : undefined} required />}
        </FormField>
      </div>
      {phoneError && <p id="phone-error" className={styles.fieldError} role="alert">{phoneError}</p>}
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
          {SERVICE_OPTIONS.map(({ value, name, description }) => {
            const Icon = SERVICE_ICONS[value]
            const checked = form.serviceType === value
            const price = prices[value]
            return (
              <label key={value} className={`${styles.option} ${checked ? styles.optionChecked : ''}`}>
                <input type="radio" name="serviceType" value={value} checked={checked} onChange={update('serviceType')} className="visually-hidden" />
                <Icon size={20} className={styles.optionIcon} aria-hidden="true" />
                <span className={styles.optionName}>{name}</span>
                <span className={styles.optionText}>{description}</span>
                {price && <span className={styles.optionPrice}>From {formatMoney(price.fromPrice, price.currency)}</span>}
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
    ['Recipient', form.recipientName],
    ['Recipient phone', form.recipientPhone],
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

function PriceEstimate({ form, fee, isLoading, error }) {
  const option = findServiceOption(form.serviceType)
  return (
    <Card className={styles.estimate}>
      <CardHeader title="Price Estimate" icon={Tag} />
      {error && <Alert tone="error" title="No estimate available">{error}</Alert>}
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
        {fee.estimatedDeliveryAt && <div><dt>Estimated delivery</dt><dd>{formatFullDate(fee.estimatedDeliveryAt)}</dd></div>}
      </dl>
      : !error && <p className={styles.note}>Select zones and enter a valid parcel weight to see your estimate.</p>}
      {fee && !isLoading && <div className={styles.total}>
        <span>Estimated Total</span>
        <strong>{formatMoney(fee.totalFee, fee.currency)}</strong>
      </div>}
      <p className={styles.note}>
        <Info size={15} aria-hidden="true" /> The first 1 kg is included in the base fee. Delivery dates count working days (Monday to Friday).
      </p>
    </Card>
  )
}

/** Customer booking flow backed by the booking API. */
export default function CreateDeliveryPage() {
  const navigate = useNavigate()
  const prices = useServicePrices()
  const [step, setStep] = useState(0)
  const [form, setForm] = useState(INITIAL_FORM)
  const [zones, setZones] = useState([])
  const [estimate, setEstimate] = useState({ key: '', fee: null, error: '' })
  const [isCreating, setIsCreating] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    getZones({ signal: controller.signal })
      .then(setZones)
      .catch((caught) => {
        if (!controller.signal.aborted) setError(apiMessage(caught, 'Could not load delivery zones. Please try again.'))
      })
    return () => controller.abort()
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
  const phoneValid = PHONE_PATTERN.test(form.recipientPhone.trim())
  const phoneError = form.recipientPhone && !phoneValid ? 'Enter a phone number of 7 to 20 digits, spaces or dashes.' : ''
  // The estimate belongs to one exact set of inputs; a result for older inputs is never shown.
  const estimateKey = form.pickupZoneId && form.dropoffZoneId && weightValid
    ? [form.pickupZoneId, form.dropoffZoneId, form.parcelSize, weight, form.serviceType].join('|')
    : ''
  const isCurrentEstimate = Boolean(estimateKey) && estimate.key === estimateKey
  const fee = isCurrentEstimate ? estimate.fee : null
  const estimateError = isCurrentEstimate ? estimate.error : ''
  const isEstimating = Boolean(estimateKey) && !isCurrentEstimate
  const detailsComplete = form.pickupAddress.trim() && form.dropoffAddress.trim() && form.pickupZoneId && form.dropoffZoneId
    && form.recipientName.trim() && phoneValid && weightValid && fee && !isEstimating

  useEffect(() => {
    if (!estimateKey) return undefined
    const [pickupZoneId, dropoffZoneId, parcelSize, weightKg, serviceType] = estimateKey.split('|')
    const controller = new AbortController()
    const timer = setTimeout(() => {
      estimateFee({ pickupZoneId, dropoffZoneId, parcelSize, weightKg: Number(weightKg), serviceType }, { signal: controller.signal })
        .then((result) => setEstimate({ key: estimateKey, fee: result, error: '' }))
        .catch((caught) => {
          if (!controller.signal.aborted) {
            setEstimate({ key: estimateKey, fee: null, error: apiMessage(caught, 'Could not estimate the delivery fee. Please try again.') })
          }
        })
    }, 300)
    return () => {
      controller.abort()
      clearTimeout(timer)
    }
  }, [estimateKey])

  function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setStep((current) => Math.min(current + 1, STEPS.length - 1))
  }

  async function handleConfirm() {
    setIsCreating(true)
    setError('')
    const request = {
      pickupZoneId: form.pickupZoneId,
      dropoffZoneId: form.dropoffZoneId,
      pickupZoneName: zones.find(({ id }) => id === form.pickupZoneId)?.name,
      dropoffZoneName: zones.find(({ id }) => id === form.dropoffZoneId)?.name,
      parcelSize: form.parcelSize,
      weightKg: weight,
      serviceType: form.serviceType,
      pickupAddress: form.pickupAddress.trim(),
      dropoffAddress: form.dropoffAddress.trim(),
      recipientName: form.recipientName.trim(),
      recipientPhone: form.recipientPhone.trim(),
    }
    try {
      const parcel = await createParcel(request)
      navigate(ROUTES.bookingConfirmation(parcel.trackingNumber), { state: { parcel, request, fee } })
    } catch (caught) {
      setError(apiMessage(caught, 'Could not create your booking. Please try again.'))
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
          {step === 0 && <DetailsStep form={form} update={update} zones={zones} weightError={weightError} phoneError={phoneError} prices={prices} />}
          {step >= 1 && <ReviewStep form={form} zones={zones} />}
          {step === 2 && (
            <p className={styles.confirmText}>
              <CalendarClock size={15} aria-hidden="true" /> Confirm this delivery to create your parcel booking.
            </p>
          )}
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
        <PriceEstimate form={form} fee={fee} isLoading={isEstimating} error={estimateError} />
      </div>
    </>
  )
}
