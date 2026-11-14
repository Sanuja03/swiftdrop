import { isFinished } from '@/shared/domain/parcelStatus'
import ShipmentListPage from './ShipmentListPage'

export default function DeliveryHistoryPage() {
  return (
    <ShipmentListPage
      title="Delivery History"
      subtitle="Completed deliveries. Download a PDF receipt for any of them."
      filter={(shipment) => isFinished(shipment.status)}
      emptyTitle="No completed deliveries yet"
      emptyText="Delivered parcels and their receipts will appear here."
      tableProps={{ showService: true, showReceipt: true }}
    />
  )
}
