import { useState, useMemo } from 'react'
import { Package, Search, ArrowRight, UserCheck } from 'lucide-react'
import { formatDayTime, formatMoney } from '@/shared/utils/format'
import Button from '@/shared/components/ui/Button'
import EmptyState from '@/shared/components/ui/EmptyState'
import styles from './UnassignedParcelsTable.module.css'

export default function UnassignedParcelsTable({ parcels = [], onAssignClick }) {
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedService, setSelectedService] = useState('ALL')

  const filteredParcels = useMemo(() => {
    return parcels.filter((parcel) => {
      // Service filter
      if (selectedService !== 'ALL' && parcel.serviceType !== selectedService) {
        return false
      }

      // Search query filter
      if (!searchQuery.trim()) {
        return true
      }

      const q = searchQuery.toLowerCase().trim()
      const tracking = (parcel.trackingNumber || '').toLowerCase()
      const recipient = (parcel.recipientName || '').toLowerCase()
      const phone = (parcel.recipientPhone || '').toLowerCase()
      const sender = (parcel.senderName || '').toLowerCase()
      const pickup = (parcel.pickupCity || '').toLowerCase()
      const dropoff = (parcel.dropoffCity || '').toLowerCase()

      return (
        tracking.includes(q) ||
        recipient.includes(q) ||
        phone.includes(q) ||
        sender.includes(q) ||
        pickup.includes(q) ||
        dropoff.includes(q)
      )
    })
  }, [parcels, searchQuery, selectedService])

  const getServiceBadgeClass = (serviceType) => {
    switch (serviceType) {
      case 'EXPRESS':
        return styles.serviceExpress
      case 'SAME_DAY':
        return styles.serviceSameDay
      default:
        return styles.serviceStandard
    }
  }

  return (
    <div className={styles.container}>
      {/* Search and Filters Toolbar */}
      <div className={styles.toolbar}>
        <div className={styles.filterGroup}>
          <div className={styles.searchBox}>
            <Search size={16} className={styles.searchIcon} aria-hidden="true" />
            <input
              type="text"
              className={styles.searchInput}
              placeholder="Search tracking #, city, recipient..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Filter unassigned parcels"
            />
          </div>

          <select
            className={styles.filterSelect}
            value={selectedService}
            onChange={(e) => setSelectedService(e.target.value)}
            aria-label="Filter by service level"
          >
            <option value="ALL">All Service Types</option>
            <option value="STANDARD">Standard</option>
            <option value="EXPRESS">Express</option>
            <option value="SAME_DAY">Same Day</option>
          </select>
        </div>

        <span className={styles.resultCount}>
          Showing {filteredParcels.length} of {parcels.length} unassigned
        </span>
      </div>

      {/* Parcels Table or Empty State */}
      {filteredParcels.length === 0 ? (
        <EmptyState
          icon={Package}
          title="No unassigned parcels"
        >
          {parcels.length === 0
            ? 'There are currently no parcels waiting for courier assignment in the dispatch queue.'
            : 'No unassigned parcels matched your search criteria.'}
        </EmptyState>
      ) : (
        <div className={styles.scroller}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">Tracking No.</th>
                <th scope="col">Route</th>
                <th scope="col">Recipient</th>
                <th scope="col">Service</th>
                <th scope="col">Weight & Fee</th>
                <th scope="col">Created</th>
                <th scope="col" style={{ textAlign: 'right' }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {filteredParcels.map((parcel) => (
                <tr key={parcel.id || parcel.trackingNumber}>
                  <td>
                    <span className={styles.trackingLink}>
                      <Package size={16} color="var(--color-orange, #e06a1f)" aria-hidden="true" />
                      {parcel.trackingNumber}
                    </span>
                  </td>
                  <td>
                    <div className={styles.routeCell}>
                      <span>{parcel.pickupCity || 'Unknown'}</span>
                      <ArrowRight size={14} className={styles.arrowIcon} aria-hidden="true" />
                      <span>{parcel.dropoffCity || 'Unknown'}</span>
                    </div>
                  </td>
                  <td>
                    <div className={styles.contactCell}>
                      <span className={styles.contactName}>{parcel.recipientName}</span>
                      <span className={styles.contactPhone}>{parcel.recipientPhone}</span>
                    </div>
                  </td>
                  <td>
                    <span
                      className={`${styles.serviceBadge} ${getServiceBadgeClass(parcel.serviceType)}`}
                    >
                      {parcel.serviceType || 'STANDARD'}
                    </span>
                  </td>
                  <td>
                    <div>
                      <div className={styles.feeCell}>
                        {parcel.fee != null ? formatMoney(parcel.fee) : 'N/A'}
                      </div>
                      <div className={styles.weightText}>
                        {parcel.weightKg != null ? `${parcel.weightKg} kg` : ''}
                      </div>
                    </div>
                  </td>
                  <td>
                    <span className={styles.dateText}>
                      {parcel.createdAt ? formatDayTime(parcel.createdAt) : 'N/A'}
                    </span>
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <Button
                      variant="primary"
                      size="sm"
                      icon={UserCheck}
                      onClick={() => onAssignClick(parcel)}
                    >
                      Assign
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
