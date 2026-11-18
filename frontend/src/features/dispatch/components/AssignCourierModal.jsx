import { useState } from 'react'
import { X, CheckCircle2 } from 'lucide-react'
import { assignCourier } from '../api/dispatchApi'
import Button from '@/shared/components/ui/Button'
import Alert from '@/shared/components/ui/Alert'
import styles from './AssignCourierModal.module.css'

export default function AssignCourierModal({
  parcel,
  couriers = [],
  isOpen,
  onClose,
  onAssignSuccess,
}) {
  const [courierId, setCourierId] = useState('')
  const [notes, setNotes] = useState('')
  const [validationError, setValidationError] = useState('')
  const [apiError, setApiError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')
  const [prevParcelId, setPrevParcelId] = useState(parcel?.id)

  if (parcel?.id !== prevParcelId) {
    setPrevParcelId(parcel?.id)
    setCourierId('')
    setNotes('')
    setValidationError('')
    setApiError('')
    setIsSubmitting(false)
    setSuccessMessage('')
  }

  if (!isOpen || !parcel) {
    return null
  }

  const handleNotesChange = (e) => {
    const val = e.target.value
    if (val.length <= 500) {
      setNotes(val)
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setValidationError('')
    setApiError('')

    if (!courierId) {
      setValidationError('Please select a courier to assign this parcel.')
      return
    }

    if (isSubmitting) {
      return
    }

    setIsSubmitting(true)

    try {
      const response = await assignCourier({
        parcelId: parcel.id,
        courierId,
        notes: notes.trim() || undefined,
      })

      const selectedCourier = couriers.find((c) => c.id === courierId)
      const courierName = selectedCourier ? selectedCourier.fullName : 'the courier'
      setSuccessMessage(`Assigned to ${courierName} successfully!`)

      setTimeout(() => {
        if (onAssignSuccess) {
          onAssignSuccess(parcel, response)
        }
        onClose()
      }, 700)
    } catch (err) {
      setApiError(err.message || 'Failed to assign courier. Please try again.')
      setIsSubmitting(false)
    }
  }

  const getServiceBadgeClass = (serviceType) => {
    switch (serviceType) {
      case 'EXPRESS':
        return styles.serviceExpress
      case 'SAME_DAY':
        return styles.serviceSameDay
      default:
        return styles.serviceStandard
    }
  }

  return (
    <div
      className={styles.backdrop}
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-assign-title"
    >
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <header className={styles.header}>
          <h2 id="modal-assign-title" className={styles.title}>
            Assign Courier
          </h2>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
            aria-label="Close assignment dialog"
            disabled={isSubmitting}
          >
            <X size={20} />
          </button>
        </header>

        {/* Selected Parcel Summary */}
        <div className={styles.parcelSummary}>
          <div className={styles.trackingRow}>
            <span className={styles.trackingNumber}>{parcel.trackingNumber}</span>
            <span
              className={`${styles.serviceBadge} ${getServiceBadgeClass(parcel.serviceType)}`}
            >
              {parcel.serviceType || 'STANDARD'}
            </span>
          </div>
          <p className={styles.routeText}>
            <strong>Route:</strong> {parcel.pickupCity || 'Unknown'} →{' '}
            {parcel.dropoffCity || 'Unknown'}
          </p>
          <p className={styles.recipientText}>
            <strong>Recipient:</strong> {parcel.recipientName} ({parcel.recipientPhone})
          </p>
          <p className={styles.recipientText}>
            <strong>Package:</strong> {parcel.weightKg ? `${parcel.weightKg} kg` : 'N/A'}{' '}
            {parcel.fee ? `• LKR ${parcel.fee}` : ''}
          </p>
        </div>

        {apiError && (
          <div style={{ marginBottom: '16px' }}>
            <Alert tone="error" title="Assignment Error">
              {apiError}
            </Alert>
          </div>
        )}

        {successMessage && (
          <div className={styles.successBanner} role="status">
            <CheckCircle2 size={18} />
            <span>{successMessage}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.fieldGroup}>
            <label htmlFor="courier-select" className={styles.label}>
              Select Active Courier *
            </label>
            <select
              id="courier-select"
              className={styles.select}
              value={courierId}
              onChange={(e) => {
                setCourierId(e.target.value)
                if (validationError) setValidationError('')
              }}
              disabled={isSubmitting || !!successMessage}
            >
              <option value="">-- Choose an active courier --</option>
              {couriers.map((courier) => (
                <option key={courier.id} value={courier.id}>
                  {courier.fullName} {courier.phone ? `(${courier.phone})` : ''}
                </option>
              ))}
            </select>
            {validationError && (
              <p className={styles.validationError} role="alert">
                {validationError}
              </p>
            )}
          </div>

          <div className={styles.fieldGroup}>
            <label htmlFor="assignment-notes" className={styles.label}>
              Dispatcher Notes (Optional)
            </label>
            <textarea
              id="assignment-notes"
              className={styles.textarea}
              placeholder="Add optional delivery instructions or landmarks (max 500 characters)..."
              value={notes}
              onChange={handleNotesChange}
              maxLength={500}
              disabled={isSubmitting || !!successMessage}
            />
            <span className={styles.charCount}>{notes.length} / 500 characters</span>
          </div>

          <div className={styles.actions}>
            <Button
              variant="secondary"
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              variant="primary"
              type="submit"
              disabled={isSubmitting || !!successMessage}
            >
              {isSubmitting ? 'Assigning...' : 'Confirm Assignment'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
