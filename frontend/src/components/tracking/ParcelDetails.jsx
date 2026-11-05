import { CalendarClock, Package } from 'lucide-react'
import { isFinished } from '../../domain/parcelStatus'
import { formatDayTime, formatFullDate } from '../../utils/format'
import { CardHeader } from '../ui/Card'
import styles from './ParcelDetails.module.css'

function DetailRow({ label, value }) {
  return (
    <>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </>
  )
}

/** Parcel facts plus the estimated (or actual) delivery time. */
export default function ParcelDetails({ tracking }) {
  const finished = isFinished(tracking.status)
  return (
    <div className={styles.wrapper}>
      <div>
        <CardHeader title="Parcel Details" icon={Package} as="h3" />
        <dl className={styles.list}>
          <DetailRow label="Tracking No." value={tracking.trackingNumber} />
          <DetailRow label="From" value={tracking.originCity} />
          <DetailRow label="To" value={tracking.destinationCity} />
          <DetailRow label="Service" value={tracking.serviceLabel} />
        </dl>
      </div>
      <div className={styles.eta}>
        <CalendarClock size={20} className={styles.etaIcon} aria-hidden="true" />
        <div>
          <p className={styles.etaLabel}>{finished ? 'Last update' : 'Estimated delivery'}</p>
          <p className={styles.etaValue}>
            {finished || !tracking.estimatedDeliveryAt
              ? formatDayTime(tracking.lastUpdatedAt)
              : formatFullDate(tracking.estimatedDeliveryAt)}
          </p>
        </div>
      </div>
    </div>
  )
}
