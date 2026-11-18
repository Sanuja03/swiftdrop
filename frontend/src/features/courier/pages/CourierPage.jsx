import { useEffect, useState } from 'react'
import { ArrowLeft, ChevronRight, MapPin, Package, RefreshCw } from 'lucide-react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { request } from '@/shared/api/httpClient'
import Logo from '@/shared/components/brand/Logo'
import Button from '@/shared/components/ui/Button'
import StatusBadge from '@/shared/components/ui/StatusBadge'
import { ROUTES } from '@/shared/config/routes'
import DeliveryActions from './DeliveryActions'
import styles from './CourierPage.module.css'

const dateFormat = new Intl.DateTimeFormat('en-LK', {
  timeZone: 'Asia/Colombo', weekday: 'long', day: 'numeric', month: 'long',
})

export default function CourierPage() {
  const { user } = useAuth()
  const { parcelId } = useParams()

  if (user?.role !== 'ROLE_COURIER') return <Navigate to="/profile" replace />

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to={ROUTES.courier} aria-label="SwiftDrop courier deliveries"><Logo /></Link>
          <Link to="/profile" className={styles.profile}>My account</Link>
        </div>
      </header>
      <main className={styles.main}>
        <DeliveryContent key={parcelId ?? 'today'} parcelId={parcelId} />
      </main>
    </div>
  )
}

function DeliveryContent({ parcelId }) {
  const [result, setResult] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const path = parcelId ? `/courier/parcels/${encodeURIComponent(parcelId)}` : '/courier/deliveries/today'

  useEffect(() => {
    const controller = new AbortController()
    request(path, { signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setResult(data) })
      .catch((error) => { if (!controller.signal.aborted) setLoadError(error.message) })
    return () => controller.abort()
  }, [path, attempt])

  function reload() {
    setResult(null)
    setLoadError('')
    setAttempt((value) => value + 1)
  }

  return (
    <>
      {parcelId && (
        <Link to={ROUTES.courier} className={styles.back}><ArrowLeft size={17} /> Today’s deliveries</Link>
      )}
      <div className={styles.heading}>
        <div>
          <p className={styles.date}>{parcelId ? 'Courier delivery' : dateFormat.format(new Date())}</p>
          <h1>{parcelId ? result?.trackingNumber ?? 'Parcel details' : 'Today’s deliveries'}</h1>
        </div>
        {!parcelId && <Button variant="secondary" icon={RefreshCw} onClick={reload}>Refresh</Button>}
      </div>
      {loadError && (
        <div className={styles.card} role="alert">
          <p>{loadError}</p><Button variant="secondary" onClick={reload}>Try again</Button>
        </div>
      )}
      {!result && !loadError && <p role="status">Loading {parcelId ? 'parcel details' : 'deliveries'}…</p>}
      {result && (parcelId ? (
        <>
          <article className={styles.card}>
            <div className={styles.row}><h2>Delivery details</h2><StatusBadge status={result.status} /></div>
            <dl className={styles.details}>
              <div><dt>Pickup</dt><dd>{result.senderName}<br />{result.pickupAddress}<br />{result.pickupCity}</dd></div>
              <div><dt>Drop-off</dt><dd>{result.recipientName}<br />{result.dropoffAddress}<br />{result.dropoffCity}</dd></div>
              {result.recipientPhone && <div><dt>Recipient phone</dt><dd><a href={`tel:${result.recipientPhone}`}>{result.recipientPhone}</a></dd></div>}
              {result.scheduledDate && <div><dt>Delivery date</dt><dd>{result.scheduledDate}</dd></div>}
            </dl>
          </article>
          <DeliveryActions parcel={result} onStatusChange={(status) => setResult({ ...result, status })} />
        </>
      ) : (
        result.length ? (
          <ul className={styles.list}>
            {result.map((parcel) => (
              <li key={parcel.parcelId}>
                <Link to={ROUTES.courierParcel(parcel.parcelId)} className={styles.delivery}>
                  <span className={styles.parcelIcon}><Package size={22} aria-hidden="true" /></span>
                  <span className={styles.summary}>
                    <strong>{parcel.trackingNumber}</strong>
                    <span>{parcel.recipientName}</span>
                    <span className={styles.location}><MapPin size={14} aria-hidden="true" /> {parcel.dropoffCity}</span>
                  </span>
                  <StatusBadge status={parcel.status} />
                  <ChevronRight className={styles.chevron} size={17} aria-hidden="true" />
                </Link>
              </li>
            ))}
          </ul>
        ) : <div className={styles.card}><h2>No deliveries scheduled today</h2><p>New assignments scheduled for today will appear here.</p></div>
      ))}
    </>
  )
}
