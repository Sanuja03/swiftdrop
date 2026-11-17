import { CalendarClock, CircleCheck, Package, Tag } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import Alert from '@/shared/components/ui/Alert'
import Button from '@/shared/components/ui/Button'
import Card, { CardHeader } from '@/shared/components/ui/Card'
import PageHeader from '@/shared/components/ui/PageHeader'
import Spinner from '@/shared/components/ui/Spinner'
import { ROUTES } from '@/shared/config/routes'
import { findServiceOption } from '@/shared/domain/serviceOptions'
import { formatFullDate, formatMoney } from '@/shared/utils/format'
import { getBooking } from '../api/bookingApi'
import styles from './BookingConfirmationPage.module.css'

const PARCEL_LABELS = { DOCUMENTS: 'Documents', SMALL: 'Small', MEDIUM: 'Medium', LARGE: 'Large' }

/** Turns GET /parcels/{trackingNumber} into the same shape the booking flow passes in navigation state. */
function fromBookingDetails(details) {
  return {
    parcel: {
      parcelId: details.parcelId,
      trackingNumber: details.trackingNumber,
      status: details.status,
      totalFee: details.totalFee,
      currency: details.currency,
      createdAt: details.createdAt,
      estimatedDeliveryAt: details.estimatedDeliveryAt,
    },
    request: {
      pickupAddress: details.pickupAddress,
      pickupZoneName: details.pickupZoneName,
      dropoffAddress: details.dropoffAddress,
      dropoffZoneName: details.dropoffZoneName,
      recipientName: details.recipientName,
      recipientPhone: details.recipientPhone,
      parcelSize: details.parcelSize,
      weightKg: details.weightKg,
      serviceType: details.serviceType,
    },
    fee: {
      baseFee: details.baseFee,
      weightCharge: details.weightCharge,
      zoneCharge: details.zoneCharge,
      totalFee: details.totalFee,
      currency: details.currency,
    },
  }
}

/** Booking from navigation state right after booking, otherwise loaded from the API (e.g. after a refresh). */
function useBooking(trackingNumber, state) {
  const fromState = state?.parcel ? state : null
  const [loaded, setLoaded] = useState({ key: null, booking: null, error: null })

  useEffect(() => {
    if (fromState || !trackingNumber) return undefined
    const controller = new AbortController()
    getBooking(trackingNumber, { signal: controller.signal })
      .then((details) => setLoaded({ key: trackingNumber, booking: fromBookingDetails(details), error: null }))
      .catch((error) => {
        if (!controller.signal.aborted) setLoaded({ key: trackingNumber, booking: null, error })
      })
    return () => controller.abort()
  }, [fromState, trackingNumber])

  if (fromState) return { booking: fromState, error: null, isLoading: false }
  const isCurrent = loaded.key === trackingNumber
  return {
    booking: isCurrent ? loaded.booking : null,
    error: isCurrent ? loaded.error : null,
    isLoading: !isCurrent,
  }
}

export default function BookingConfirmationPage() {
  const { trackingNumber = '' } = useParams()
  const { state } = useLocation()
  const { booking, error, isLoading } = useBooking(trackingNumber, state)

  const parcel = booking?.parcel
  const request = booking?.request
  const fee = booking?.fee
  const service = request ? findServiceOption(request.serviceType) : null
  const shownTrackingNumber = parcel?.trackingNumber ?? trackingNumber.toUpperCase()

  if (isLoading) {
    return (
      <Card>
        <Spinner label="Loading your booking…" />
      </Card>
    )
  }

  if (error) {
    return (
      <>
        <PageHeader title="Booking" subtitle={`Tracking number ${shownTrackingNumber}`} />
        <Alert
          tone="error"
          title={error.isNotFound ? 'Booking not found' : 'Could not load this booking'}
          action={<Button variant="secondary" size="sm" to={ROUTES.shipments}>My Shipments</Button>}
        >
          {error.isNotFound ? 'We could not find this booking in your account.' : error.message}
        </Alert>
      </>
    )
  }

  return (
    <>
      <div className={styles.successIcon}><CircleCheck size={34} aria-hidden="true" /></div>
      <PageHeader title="Delivery booked" subtitle="Your parcel booking is confirmed." />
      <Card className={styles.confirmation}>
        <p className={styles.trackingLabel}>Tracking number</p>
        <h2 className={styles.trackingNumber}>{shownTrackingNumber}</h2>
        {parcel?.estimatedDeliveryAt && (
          <Alert tone="info" title="Estimated delivery">
            <CalendarClock size={15} aria-hidden="true" /> {formatFullDate(parcel.estimatedDeliveryAt)}
          </Alert>
        )}
        <div className={styles.columns}>
          <section>
            <CardHeader title="Parcel summary" icon={Package} />
            {request ? <dl className={styles.details}>
              <div><dt>Pickup</dt><dd>{request.pickupAddress}</dd></div>
              <div><dt>Pickup zone</dt><dd>{request.pickupZoneName}</dd></div>
              <div><dt>Drop-off</dt><dd>{request.dropoffAddress}</dd></div>
              <div><dt>Drop-off zone</dt><dd>{request.dropoffZoneName}</dd></div>
              {request.recipientName && <div><dt>Recipient</dt><dd>{request.recipientName}</dd></div>}
              {request.recipientPhone && <div><dt>Recipient phone</dt><dd>{request.recipientPhone}</dd></div>}
              <div><dt>Parcel size</dt><dd>{PARCEL_LABELS[request.parcelSize] ?? request.parcelSize}</dd></div>
              <div><dt>Weight</dt><dd>{request.weightKg} kg</dd></div>
              <div><dt>Service</dt><dd>{service?.name ?? request.serviceType}</dd></div>
            </dl> : <p className={styles.missing}>Booking details are unavailable.</p>}
          </section>
          <section>
            <CardHeader title="Fee breakdown" icon={Tag} />
            {fee ? <dl className={styles.details}>
              <div><dt>Base fee</dt><dd>{formatMoney(fee.baseFee, fee.currency)}</dd></div>
              <div><dt>Weight charge</dt><dd>{formatMoney(fee.weightCharge, fee.currency)}</dd></div>
              <div><dt>Zone charge</dt><dd>{formatMoney(fee.zoneCharge, fee.currency)}</dd></div>
              <div className={styles.total}><dt>Total fee</dt><dd>{formatMoney(parcel?.totalFee ?? fee.totalFee, parcel?.currency ?? fee.currency)}</dd></div>
            </dl> : <p className={styles.missing}>Fee details are unavailable.</p>}
          </section>
        </div>
        <div className={styles.actions}>
          <Button to={ROUTES.shipment(shownTrackingNumber)}>Track this parcel</Button>
          <Button variant="secondary" to={ROUTES.createDelivery}>Create another delivery</Button>
          <Link className={styles.textLink} to={ROUTES.app}>Back to overview</Link>
        </div>
      </Card>
    </>
  )
}
