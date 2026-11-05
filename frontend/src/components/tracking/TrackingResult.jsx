import { Package } from 'lucide-react'
import Card, { CardHeader } from '../ui/Card'
import StatusBadge from '../ui/StatusBadge'
import LiveBadge from './LiveBadge'
import ParcelDetails from './ParcelDetails'
import RouteMap from './RouteMap'
import ShipmentStepper from './ShipmentStepper'
import TrackingHistory from './TrackingHistory'
import styles from './TrackingResult.module.css'

/** Full tracking view: status stepper, parcel details, route map and history. */
export default function TrackingResult({ tracking, isLive }) {
  return (
    <div className={styles.grid}>
      <div className={styles.main}>
        <Card>
          <CardHeader
            title="Shipment Status"
            icon={Package}
            action={
              <div className={styles.badges}>
                <StatusBadge status={tracking.status} />
                <LiveBadge isLive={isLive} />
              </div>
            }
          />
          <ShipmentStepper status={tracking.status} timeline={tracking.timeline} />
        </Card>
        <Card>
          <ParcelDetails tracking={tracking} />
        </Card>
      </div>
      <Card className={styles.mapCard}>
        <RouteMap
          status={tracking.status}
          originCity={tracking.originCity}
          destinationCity={tracking.destinationCity}
        />
      </Card>
      <Card className={styles.history}>
        <TrackingHistory timeline={tracking.timeline} />
      </Card>
    </div>
  )
}
