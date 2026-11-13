import { ChevronRight, Package } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import { formatDayTime } from '@/shared/utils/format'
import StatusBadge from '@/shared/components/ui/StatusBadge'
import { ReceiptButton } from '@/features/receipts'
import styles from './ShipmentsTable.module.css'

/**
 * Shipments list used on the dashboard, My Shipments and Delivery History.
 * Each tracking number links to its live tracking page.
 */
export default function ShipmentsTable({ shipments, showService = false, showReceipt = false }) {
  return (
    <div className={styles.scroller}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">Tracking No.</th>
            <th scope="col">To</th>
            {showService && <th scope="col">Service</th>}
            <th scope="col">Status</th>
            <th scope="col">Updated</th>
            <th scope="col">
              <span className="visually-hidden">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {shipments.map((shipment) => (
            <tr key={shipment.trackingNumber}>
              <td>
                <Link to={ROUTES.shipment(shipment.trackingNumber)} className={styles.tracking}>
                  <Package size={16} aria-hidden="true" />
                  {shipment.trackingNumber}
                </Link>
              </td>
              <td>{shipment.destinationCity}</td>
              {showService && <td className={styles.muted}>{shipment.serviceLabel}</td>}
              <td>
                <StatusBadge status={shipment.status} />
              </td>
              <td className={styles.muted}>{formatDayTime(shipment.lastUpdatedAt)}</td>
              <td className={styles.actions}>
                {showReceipt ? (
                  <ReceiptButton trackingNumber={shipment.trackingNumber} />
                ) : (
                  <Link
                    to={ROUTES.shipment(shipment.trackingNumber)}
                    className={styles.open}
                    aria-label={`Track ${shipment.trackingNumber}`}
                  >
                    <ChevronRight size={18} aria-hidden="true" />
                  </Link>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
