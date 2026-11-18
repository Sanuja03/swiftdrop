import { Mail, Phone, Package, CheckCircle2, Clock, AlertTriangle } from 'lucide-react'
import styles from './WorkloadCard.module.css'

const STATUS_CONFIG = {
  AVAILABLE: {
    label: 'AVAILABLE',
    description: '0 active assignments',
    tone: 'available',
    icon: CheckCircle2,
  },
  MODERATE: {
    label: 'MODERATE',
    description: '1–3 active assignments',
    tone: 'moderate',
    icon: Clock,
  },
  BUSY: {
    label: 'BUSY',
    description: '> 3 active assignments',
    tone: 'busy',
    icon: AlertTriangle,
  },
}

export default function WorkloadCard({ workload }) {
  if (!workload) return null

  const {
    courierId,
    courierName,
    courierEmail,
    courierPhone,
    activeAssignmentsCount = 0,
    workloadStatus = 'AVAILABLE',
  } = workload

  const normalizedStatus = (workloadStatus || '').toUpperCase()
  const config = STATUS_CONFIG[normalizedStatus] || STATUS_CONFIG.AVAILABLE
  const StatusIcon = config.icon

  // Courier initials for avatar
  const initials = courierName
    ? courierName
        .trim()
        .split(/\s+/)
        .map((part) => part[0])
        .filter(Boolean)
        .slice(0, 2)
        .join('')
        .toUpperCase()
    : 'C'

  return (
    <div
      className={`${styles.card} ${styles[config.tone]}`}
      data-testid={`workload-card-${courierId || courierEmail}`}
    >
      <div className={styles.header}>
        <div className={styles.courierInfo}>
          <div className={styles.avatar} aria-hidden="true">
            {initials}
          </div>
          <div className={styles.nameBlock}>
            <h3 className={styles.name}>{courierName || 'Unnamed Courier'}</h3>
            <span className={styles.statusDescription}>{config.description}</span>
          </div>
        </div>
        <span
          className={`${styles.statusBadge} ${styles[`badge_${config.tone}`]}`}
          data-testid="workload-status-badge"
        >
          <StatusIcon size={14} aria-hidden="true" />
          {config.label}
        </span>
      </div>

      <div className={styles.contactDetails}>
        <div className={styles.contactItem} title={courierEmail || ''}>
          <Mail size={14} className={styles.contactIcon} aria-hidden="true" />
          <span className={styles.contactText}>
            {courierEmail || 'No email provided'}
          </span>
        </div>
        <div className={styles.contactItem}>
          <Phone size={14} className={styles.contactIcon} aria-hidden="true" />
          <span className={styles.contactText}>
            {courierPhone || 'No phone number'}
          </span>
        </div>
      </div>

      <div className={styles.assignmentCountBox}>
        <div className={styles.countInfo}>
          <span className={styles.countLabel}>
            <Package size={15} aria-hidden="true" />
            Active Assignments
          </span>
          <span
            className={styles.countValue}
            data-testid="active-assignments-count"
          >
            {activeAssignmentsCount}
          </span>
        </div>
      </div>
    </div>
  )
}
