import { MapPin, Truck } from 'lucide-react'
import { journeyProgress } from '../../domain/parcelStatus'
import styles from './RouteMap.module.css'

/** Simplified route drawn as an SVG; the van moves along it as the parcel progresses. */
const ROUTE = [
  [30, 200],
  [80, 150],
  [130, 170],
  [185, 120],
  [240, 135],
  [300, 80],
  [350, 50],
]
const STREETS = [
  'M0 60 L400 20',
  'M0 140 L400 90',
  'M0 230 L400 170',
  'M60 0 L110 260',
  'M180 0 L220 260',
  'M300 0 L320 260',
]

function pointAt(progress) {
  const lengths = ROUTE.slice(1).map(([x, y], i) => Math.hypot(x - ROUTE[i][0], y - ROUTE[i][1]))
  let remaining = lengths.reduce((sum, length) => sum + length, 0) * progress
  for (let i = 0; i < lengths.length; i += 1) {
    if (remaining <= lengths[i]) {
      const t = lengths[i] === 0 ? 0 : remaining / lengths[i]
      const [x1, y1] = ROUTE[i]
      const [x2, y2] = ROUTE[i + 1]
      return [x1 + (x2 - x1) * t, y1 + (y2 - y1) * t]
    }
    remaining -= lengths[i]
  }
  return ROUTE.at(-1)
}

export default function RouteMap({ status, originCity, destinationCity, compact = false }) {
  const [vanX, vanY] = pointAt(journeyProgress(status))
  const [endX, endY] = ROUTE.at(-1)
  const [startX, startY] = ROUTE[0]
  const routePath = `M${ROUTE.map(([x, y]) => `${x} ${y}`).join(' L')}`

  return (
    <div className={`${styles.map} ${compact ? styles.compact : ''}`}>
      <svg viewBox="0 0 400 260" preserveAspectRatio="xMidYMid slice" className={styles.svg} role="img"
        aria-label={`Route from ${originCity ?? 'pickup'} to ${destinationCity ?? 'destination'}`}>
        <rect width="400" height="260" className={styles.ground} />
        <path d="M250 180 Q300 160 340 200 T400 210 V260 H230 Z" className={styles.park} />
        {STREETS.map((d) => (
          <path key={d} d={d} className={styles.street} />
        ))}
        <path d={routePath} className={styles.routeTrack} />
        <path d={routePath} className={styles.route} />
        <circle cx={startX} cy={startY} r="6" className={styles.origin} />
      </svg>
      <span className={styles.pin} style={{ left: `${(endX / 400) * 100}%`, top: `${(endY / 260) * 100}%` }}>
        <MapPin size={30} fill="currentColor" stroke="#fff" strokeWidth={1.5} aria-hidden="true" />
      </span>
      <span className={styles.van} style={{ left: `${(vanX / 400) * 100}%`, top: `${(vanY / 260) * 100}%` }}>
        <Truck size={18} aria-hidden="true" />
      </span>
      {originCity && destinationCity && (
        <p className={styles.legend}>
          {originCity} <span aria-hidden="true">→</span> {destinationCity}
        </p>
      )}
    </div>
  )
}
