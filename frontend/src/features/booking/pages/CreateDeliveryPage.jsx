import { ArrowLeft, ArrowRight, Info, MapPin, Package, Scale, Tag } from 'lucide-react'
import { useState } from 'react'
import { SERVICE_ICONS } from '@/shared/components/brand/serviceIcons'
import Alert from '@/shared/components/ui/Alert'
import Button from '@/shared/components/ui/Button'
import Card, { CardHeader } from '@/shared/components/ui/Card'
import FormField from '@/shared/components/ui/FormField'
import { Input, Select } from '@/shared/components/ui/Input'
import PageHeader from '@/shared/components/ui/PageHeader'
import { findServiceOption, SERVICE_OPTIONS } from '@/shared/domain/serviceOptions'
import { formatMoney } from '@/shared/utils/format'
import styles from './CreateDeliveryPage.module.css'

const STEPS = ['Details', 'Review', 'Confirm']
const PARCEL_TYPES = ['Documents', 'Small (up to 2 kg)', 'Medium (2-10 kg)', 'Large (10-30 kg)']
const INITIAL_FORM = {
  pickupAddress: '',
  dropoffAddress: '',
  parcelType: PARCEL_TYPES[1],
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

function DetailsStep({ form, update }) {
  return (
    <div className={styles.fields}>
      <FormField label="Pickup Address" icon={MapPin}>
        {(id) => (
          <Input id={id} value={form.pickupAddress} onChange={update('pickupAddress')} placeholder="No. 415, Cotta Road, Rajagiriya" required />
        )}
      </FormField>
      <FormField label="Drop-off Address" icon={MapPin}>
        {(id) => (
          <Input id={id} value={form.dropoffAddress} onChange={update('dropoffAddress')} placeholder="No. 12, Galle Road, Dehiwala" required />
        )}
      </FormField>
      <div className={styles.row}>
        <FormField label="Parcel Details" icon={Package}>
          {(id) => (
            <Select id={id} value={form.parcelType} onChange={update('parcelType')}>
              {PARCEL_TYPES.map((type) => (
                <option key={type}>{type}</option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="Weight (kg)" icon={Scale}>
          {(id) => (
            <Input id={id} type="number" min="0.1" step="0.1" inputMode="decimal" value={form.weightKg} onChange={update('weightKg')} placeholder="5.0" required />
          )}
        </FormField>
      </div>
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

function ReviewStep({ form }) {
  const rows = [
    ['Pickup', form.pickupAddress],
    ['Drop-off', form.dropoffAddress],
    ['Parcel', form.parcelType],
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

function PriceEstimate({ form }) {
  const option = findServiceOption(form.serviceType)
  return (
    <Card className={styles.estimate}>
      <CardHeader title="Price Estimate" icon={Tag} />
      <dl className={styles.estimateRows}>
        <div>
          <dt>Delivery Option</dt>
          <dd>{option.name}</dd>
        </div>
        <div>
          <dt>Starting price</dt>
          <dd>{formatMoney(option.fromPrice)}</dd>
        </div>
        <div>
          <dt>Weight</dt>
          <dd>{form.weightKg ? `${form.weightKg} kg` : '–'}</dd>
        </div>
      </dl>
      <div className={styles.total}>
        <span>Estimated Total</span>
        <strong>{formatMoney(option.fromPrice)}</strong>
      </div>
      <p className={styles.note}>
        <Info size={15} aria-hidden="true" /> Final price will be confirmed after pickup.
      </p>
    </Card>
  )
}

/** Booking form (screens only; booking itself is handled by the booking module). */
export default function CreateDeliveryPage() {
  const [step, setStep] = useState(0)
  const [form, setForm] = useState(INITIAL_FORM)

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }))
  const detailsComplete = form.pickupAddress.trim() && form.dropoffAddress.trim() && Number(form.weightKg) > 0

  function handleSubmit(event) {
    event.preventDefault()
    setStep((current) => Math.min(current + 1, STEPS.length - 1))
  }

  return (
    <>
      <PageHeader title="Create a Delivery" subtitle="Book a parcel pickup in a few simple steps." />
      <div className={styles.layout}>
        <Card as="form" onSubmit={handleSubmit} className={styles.formCard}>
          <Stepper current={step} />
          {step === 0 && <DetailsStep form={form} update={update} />}
          {step >= 1 && <ReviewStep form={form} />}
          {step === 2 && (
            <Alert tone="info" title="Online booking is coming soon">
              Your delivery details are ready. Booking will be enabled here once the booking service goes live.
            </Alert>
          )}
          <div className={styles.actions}>
            {step > 0 && (
              <Button variant="secondary" icon={ArrowLeft} onClick={() => setStep(step - 1)}>
                Back
              </Button>
            )}
            {step < STEPS.length - 1 && (
              <Button type="submit" iconAfter={ArrowRight} disabled={!detailsComplete}>
                Continue
              </Button>
            )}
          </div>
        </Card>
        <PriceEstimate form={form} />
      </div>
    </>
  )
}
