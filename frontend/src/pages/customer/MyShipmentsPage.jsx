import { useState } from 'react'
import { isActive, isFinished } from '../../domain/parcelStatus'
import ShipmentListPage from './ShipmentListPage'
import styles from './MyShipmentsPage.module.css'

const FILTERS = {
  all: { label: 'All', matches: () => true },
  active: { label: 'On the way', matches: (shipment) => isActive(shipment.status) },
  finished: { label: 'Completed', matches: (shipment) => isFinished(shipment.status) },
}

export default function MyShipmentsPage() {
  const [filterKey, setFilterKey] = useState('all')

  const toolbar = (
    <div className={styles.tabs} role="tablist" aria-label="Filter shipments">
      {Object.entries(FILTERS).map(([key, { label }]) => (
        <button
          key={key}
          type="button"
          role="tab"
          aria-selected={filterKey === key}
          className={`${styles.tab} ${filterKey === key ? styles.selected : ''}`}
          onClick={() => setFilterKey(key)}
        >
          {label}
        </button>
      ))}
    </div>
  )

  return (
    <ShipmentListPage
      title="My Shipments"
      subtitle="Every parcel you have sent. Select one to follow it live."
      toolbar={toolbar}
      filter={FILTERS[filterKey].matches}
      emptyTitle="No shipments here"
      emptyText="Try another filter, or book a new delivery."
      tableProps={{ showService: true }}
    />
  )
}
