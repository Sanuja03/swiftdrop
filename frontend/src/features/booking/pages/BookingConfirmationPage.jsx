import { CircleCheck, Package, Tag } from 'lucide-react'
import { useMemo } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import Button from '@/shared/components/ui/Button'
import Card, { CardHeader } from '@/shared/components/ui/Card'
import PageHeader from '@/shared/components/ui/PageHeader'
import { ROUTES } from '@/shared/config/routes'
import { findServiceOption } from '@/shared/domain/serviceOptions'
import { formatMoney } from '@/shared/utils/format'
import styles from './BookingConfirmationPage.module.css'

const PARCEL_LABELS = { DOCUMENTS: 'Documents', SMALL: 'Small', MEDIUM: 'Medium', LARGE: 'Large' }

export default function BookingConfirmationPage() {
  const { trackingNumber = '' } = useParams()
  const { state } = useLocation()
  const booking = useMemo(() => {
    if (state?.parcel) return state
    try {
      return JSON.parse(sessionStorage.getItem(`booking:${trackingNumber}`) || 'null')
    } catch {
      return null
    }
  }, [state, trackingNumber])

  const parcel = booking?.parcel
  const request = booking?.request
  const fee = booking?.fee
  const service = request ? findServiceOption(request.serviceType) : null

  return (
    <>
      <div className={styles.successIcon}><CircleCheck size={34} aria-hidden="true" /></div>
      <PageHeader title="Delivery booked" subtitle="Your parcel booking is confirmed." />
      <Card className={styles.confirmation}>
        <p className={styles.trackingLabel}>Tracking number</p>
        <h2 className={styles.trackingNumber}>{parcel?.trackingNumber ?? trackingNumber.toUpperCase()}</h2>
        <div className={styles.columns}>
          <section>
            <CardHeader title="Parcel summary" icon={Package} />
            {request ? <dl className={styles.details}>
              <div><dt>Pickup</dt><dd>{request.pickupAddress}</dd></div>
              <div><dt>Pickup zone</dt><dd>{request.pickupZoneName ?? request.pickupZoneId}</dd></div>
              <div><dt>Drop-off</dt><dd>{request.dropoffAddress}</dd></div>
              <div><dt>Drop-off zone</dt><dd>{request.dropoffZoneName ?? request.dropoffZoneId}</dd></div>
              <div><dt>Parcel size</dt><dd>{PARCEL_LABELS[request.parcelSize] ?? request.parcelSize}</dd></div>
              <div><dt>Weight</dt><dd>{request.weightKg} kg</dd></div>
              <div><dt>Service</dt><dd>{service?.name ?? request.serviceType}</dd></div>
            </dl> : <p className={styles.missing}>Booking details are unavailable in this session.</p>}
          </section>
          <section>
            <CardHeader title="Fee breakdown" icon={Tag} />
            {fee ? <dl className={styles.details}>
              <div><dt>Base fee</dt><dd>{formatMoney(fee.baseFee, fee.currency)}</dd></div>
              <div><dt>Weight charge</dt><dd>{formatMoney(fee.weightCharge, fee.currency)}</dd></div>
              <div><dt>Zone charge</dt><dd>{formatMoney(fee.zoneCharge, fee.currency)}</dd></div>
              <div className={styles.total}><dt>Total fee</dt><dd>{formatMoney(parcel?.totalFee ?? fee.totalFee, parcel?.currency ?? fee.currency)}</dd></div>
            </dl> : <p className={styles.missing}>Fee details are unavailable in this session.</p>}
          </section>
        </div>
        <div className={styles.actions}>
          <Button to={ROUTES.shipment(parcel?.trackingNumber ?? trackingNumber)}>Track this parcel</Button>
          <Button variant="secondary" to={ROUTES.createDelivery}>Create another delivery</Button>
          <Link className={styles.textLink} to={ROUTES.app}>Back to overview</Link>
        </div>
      </Card>
    </>
  )
}
