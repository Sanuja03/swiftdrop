import { ArrowLeft } from 'lucide-react'
import { useNavigate, useParams } from 'react-router-dom'
import TrackingFeedback from '../../components/tracking/TrackingFeedback'
import TrackingResult from '../../components/tracking/TrackingResult'
import ReceiptButton from '../../components/shipments/ReceiptButton'
import Button from '../../components/ui/Button'
import PageHeader from '../../components/ui/PageHeader'
import { ROUTES } from '../../config/routes'
import { isFinished } from '../../domain/parcelStatus'
import { useParcelTracking } from '../../hooks/useParcelTracking'
import styles from './LiveTrackingPage.module.css'

/** Live view of one of the customer's shipments. */
export default function LiveTrackingPage() {
  const { trackingNumber } = useParams()
  const navigate = useNavigate()
  const { data, error, isLoading, isLive } = useParcelTracking(trackingNumber)

  return (
    <>
      <Button variant="ghost" size="sm" icon={ArrowLeft} onClick={() => navigate(ROUTES.shipments)}>
        My Shipments
      </Button>
      <div className={styles.header}>
        <PageHeader
          title={`Shipment ${trackingNumber.toUpperCase()}`}
          subtitle="This page updates by itself whenever the courier changes the status."
          action={data && isFinished(data.status) && <ReceiptButton trackingNumber={data.trackingNumber} />}
        />
      </div>
      {data ? (
        <TrackingResult tracking={data} isLive={isLive} />
      ) : (
        <TrackingFeedback isLoading={isLoading} error={error} trackingNumber={trackingNumber} />
      )}
    </>
  )
}
