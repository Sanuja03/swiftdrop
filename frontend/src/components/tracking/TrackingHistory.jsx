import { History } from 'lucide-react'
import { statusTone } from '../../domain/parcelStatus'
import { formatDayTime } from '../../utils/format'
import { CardHeader } from '../ui/Card'
import styles from './TrackingHistory.module.css'

/** Every recorded status change, newest first. */
export default function TrackingHistory({ timeline }) {
  const entries = [...timeline].reverse()
  return (
    <div>
      <CardHeader title="Tracking History" icon={History} as="h3" />
      <ol className={styles.list}>
        {entries.map((entry, index) => (
          <li key={`${entry.status}-${entry.changedAt}`} className={styles.item}>
            <span className={`${styles.marker} ${styles[statusTone(entry.status)]} ${index === 0 ? styles.latest : ''}`} />
            <div>
              <p className={styles.label}>{entry.label}</p>
              <p className={styles.time}>{formatDayTime(entry.changedAt)}</p>
            </div>
          </li>
        ))}
      </ol>
    </div>
  )
}
