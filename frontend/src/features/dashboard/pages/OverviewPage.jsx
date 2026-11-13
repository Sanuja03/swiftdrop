import { ArrowRight, CircleCheck, Clock, Hand, Package, Truck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { RouteMap, ShipmentsTable, sortByLatestUpdate, useShipments } from '@/features/tracking'
import Alert from '@/shared/components/ui/Alert'
import Button from '@/shared/components/ui/Button'
import Card, { CardHeader } from '@/shared/components/ui/Card'
import EmptyState from '@/shared/components/ui/EmptyState'
import Spinner from '@/shared/components/ui/Spinner'
import StatCard from '@/shared/components/ui/StatCard'
import StatusBadge from '@/shared/components/ui/StatusBadge'
import { ROUTES } from '@/shared/config/routes'
import { CURRENT_CUSTOMER, CUSTOMER_TRACKING_NUMBERS } from '@/shared/data/currentCustomer'
import { isActive, PARCEL_STATUS } from '@/shared/domain/parcelStatus'
import { formatDayTime } from '@/shared/utils/format'
import styles from './OverviewPage.module.css'

const RECENT_LIMIT = 5

function LiveShipmentCard({ shipment }) {
  return (
    <Card className={styles.liveCard}>
      <CardHeader title="Track in Real Time" icon={Truck} />
      {shipment ? (
        <>
          <RouteMap
            compact
            status={shipment.status}
            originCity={shipment.originCity}
            destinationCity={shipment.destinationCity}
          />
          <Link to={ROUTES.shipment(shipment.trackingNumber)} className={styles.liveLink}>
            <div>
              <p className={styles.liveNumber}>{shipment.trackingNumber}</p>
              <p className={styles.liveMeta}>Updated {formatDayTime(shipment.lastUpdatedAt)}</p>
            </div>
            <StatusBadge status={shipment.status} />
            <ArrowRight size={18} aria-hidden="true" />
          </Link>
        </>
      ) : (
        <EmptyState icon={Truck} title="Nothing on the move">
          Shipments on their way will appear here.
        </EmptyState>
      )}
    </Card>
  )
}

export default function OverviewPage() {
  const { shipments, isLoading, error } = useShipments(CUSTOMER_TRACKING_NUMBERS)
  const sorted = sortByLatestUpdate(shipments)
  const active = sorted.filter((shipment) => isActive(shipment.status))
  const delivered = sorted.filter((shipment) => shipment.status === PARCEL_STATUS.DELIVERED)
  const pending = sorted.filter((shipment) => shipment.status === PARCEL_STATUS.CREATED)

  return (
    <>
      <div className={styles.welcome}>
        <h1>
          Welcome back, {CURRENT_CUSTOMER.firstName}! <Hand size={24} className={styles.wave} aria-hidden="true" />
        </h1>
        <p>Track, send and manage your deliveries in one place.</p>
      </div>

      {error && (
        <div className={styles.alert}>
          <Alert tone="error" title="Some shipments could not be loaded">
            {error.message}
          </Alert>
        </div>
      )}

      <div className={styles.stats}>
        <StatCard label="Active Shipments" value={isLoading ? '–' : active.length} icon={Package} />
        <StatCard label="Delivered" value={isLoading ? '–' : delivered.length} icon={CircleCheck} tone="success" />
        <StatCard label="Pending Pickup" value={isLoading ? '–' : pending.length} icon={Clock} tone="warning" />
      </div>

      <div className={styles.grid}>
        <Card>
          <CardHeader
            title="Recent Shipments"
            action={
              <Button variant="ghost" size="sm" to={ROUTES.shipments} iconAfter={ArrowRight}>
                View All
              </Button>
            }
          />
          {isLoading && <Spinner label="Loading shipments…" />}
          {!isLoading && sorted.length === 0 && (
            <EmptyState
              icon={Package}
              title="No shipments yet"
              action={
                <Button to={ROUTES.createDelivery} size="sm">
                  Create a delivery
                </Button>
              }
            >
              Your parcels will show up here once you book a delivery.
            </EmptyState>
          )}
          {!isLoading && sorted.length > 0 && <ShipmentsTable shipments={sorted.slice(0, RECENT_LIMIT)} />}
        </Card>
        {isLoading ? (
          <Card>
            <Spinner />
          </Card>
        ) : (
          <LiveShipmentCard shipment={active[0]} />
        )}
      </div>
    </>
  )
}
