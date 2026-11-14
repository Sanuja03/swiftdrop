import { Clock, MapPin, ShieldCheck } from 'lucide-react'
import { useEffect } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import DeliveryVan from '../components/DeliveryVan'
import { SERVICE_ICONS } from '@/shared/components/brand/serviceIcons'
import TrackingFeedback from '../components/TrackingFeedback'
import TrackingResult from '../components/TrackingResult'
import TrackingSearchForm from '../components/TrackingSearchForm'
import { ROUTES } from '@/shared/config/routes'
import { SERVICE_OPTIONS } from '@/shared/domain/serviceOptions'
import { useParcelTracking } from '../hooks/useParcelTracking'
import { useServicePrices } from '@/features/booking'
import { formatMoney } from '@/shared/utils/format'
import styles from './TrackParcelPage.module.css'

const PROMISES = [
  { icon: MapPin, title: 'Live tracking', text: 'Follow every step from pickup to your door, updated the moment it happens.' },
  { icon: Clock, title: 'On time', text: 'Standard, express and same-day options to fit your schedule.' },
  { icon: ShieldCheck, title: 'Safe hands', text: 'Proof of delivery and a receipt for every parcel you send.' },
]

function useScrollToHash() {
  const { hash } = useLocation()
  useEffect(() => {
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: 'smooth' })
  }, [hash])
}

/** Public page: anyone can track a parcel with its tracking number, no login needed. */
export default function TrackParcelPage() {
  const { trackingNumber } = useParams()
  const navigate = useNavigate()
  const { data, error, isLoading, isLive } = useParcelTracking(trackingNumber)
  const prices = useServicePrices()
  useScrollToHash()

  return (
    <>
      <section className={styles.hero}>
        <div className={styles.heroInner}>
          <div className={styles.heroText}>
            <h1 className={styles.title}>
              Track Your <span>Parcel</span>
            </h1>
            <p className={styles.lead}>Enter your tracking number to get real-time updates on your delivery.</p>
            <TrackingSearchForm
              key={trackingNumber}
              initialValue={trackingNumber}
              onSearch={(number) => navigate(ROUTES.trackParcel(number))}
            />
          </div>
          <DeliveryVan className={styles.van} />
        </div>
      </section>

      {trackingNumber && (
        <section className={styles.result} aria-live="polite">
          {data ? (
            <TrackingResult tracking={data} isLive={isLive} />
          ) : (
            <TrackingFeedback isLoading={isLoading} error={error} trackingNumber={trackingNumber} />
          )}
        </section>
      )}

      <section id="services" className={styles.section}>
        <h2 className={styles.sectionTitle}>Our Services</h2>
        <div className={styles.cards}>
          {SERVICE_OPTIONS.map(({ value, name, description }) => {
            const Icon = SERVICE_ICONS[value]
            const price = prices[value]
            return (
              <article key={value} className={styles.card}>
                <Icon className={styles.cardIcon} size={26} aria-hidden="true" />
                <h3>{name}</h3>
                <p>{description}</p>
                {price && <p className={styles.price}>From {formatMoney(price.fromPrice, price.currency)}</p>}
              </article>
            )
          })}
        </div>
      </section>

      <section id="about" className={styles.section}>
        <h2 className={styles.sectionTitle}>Why SwiftDrop</h2>
        <div className={styles.cards}>
          {PROMISES.map(({ icon: Icon, title, text }) => (
            <article key={title} className={styles.card}>
              <Icon className={styles.cardIcon} size={26} aria-hidden="true" />
              <h3>{title}</h3>
              <p>{text}</p>
            </article>
          ))}
        </div>
      </section>
    </>
  )
}
