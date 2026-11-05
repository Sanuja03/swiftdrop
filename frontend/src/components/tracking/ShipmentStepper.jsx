import { Check, X } from 'lucide-react'
import { buildJourneySteps } from '../../domain/parcelStatus'
import { formatDayTime } from '../../utils/format'
import styles from './ShipmentStepper.module.css'

/** Horizontal progress through the delivery journey. */
export default function ShipmentStepper({ status, timeline }) {
  const steps = buildJourneySteps(status, timeline)
  return (
    <ol className={styles.stepper}>
      {steps.map((step) => (
        <li key={step.status} className={`${styles.step} ${styles[step.state]}`}>
          <span className={styles.dot} aria-hidden="true">
            {step.state === 'done' && <Check size={13} strokeWidth={3} />}
            {step.state === 'failed' && <X size={13} strokeWidth={3} />}
            {step.state === 'current' && <span className={styles.pulse} />}
          </span>
          <span className={styles.label}>{step.label}</span>
          <span className={styles.time}>{step.changedAt ? formatDayTime(step.changedAt) : ' '}</span>
          <span className="visually-hidden">
            {step.state === 'upcoming' ? 'not reached yet' : step.state}
          </span>
        </li>
      ))}
    </ol>
  )
}
