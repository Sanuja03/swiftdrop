import { Package } from 'lucide-react'
import ShipmentsTable from '../components/ShipmentsTable'
import Alert from '@/shared/components/ui/Alert'
import Card from '@/shared/components/ui/Card'
import EmptyState from '@/shared/components/ui/EmptyState'
import PageHeader from '@/shared/components/ui/PageHeader'
import Spinner from '@/shared/components/ui/Spinner'
import { sortByLatestUpdate } from '../domain/shipments'
import { useMyShipments } from '../hooks/useMyShipments'
import styles from './ShipmentListPage.module.css'

/**
 * Shared page for shipment lists ("My Shipments", "Delivery History"):
 * loads the customer's shipments and shows the ones matching `filter`.
 */
export default function ShipmentListPage({ title, subtitle, filter, toolbar, emptyTitle, emptyText, tableProps }) {
  const { shipments, isLoading, error } = useMyShipments()
  const visible = sortByLatestUpdate(shipments.filter(filter))

  return (
    <>
      <PageHeader title={title} subtitle={subtitle} action={toolbar} />
      {error && (
        <div className={styles.alert}>
          <Alert tone="error" title="Your shipments could not be loaded">
            {error.message}
          </Alert>
        </div>
      )}
      <Card>
        {isLoading && <Spinner label="Loading shipments…" />}
        {!isLoading && visible.length === 0 && (
          <EmptyState icon={Package} title={emptyTitle}>
            {emptyText}
          </EmptyState>
        )}
        {!isLoading && visible.length > 0 && <ShipmentsTable shipments={visible} {...tableProps} />}
      </Card>
    </>
  )
}
