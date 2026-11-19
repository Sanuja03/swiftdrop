import { useState, useEffect, useCallback } from 'react'
import { Package, Users, Zap, RefreshCw, CheckCircle2 } from 'lucide-react'
import { getUnassignedParcels, getActiveCouriers } from '../api/dispatchApi'
import PageHeader from '@/shared/components/ui/PageHeader'
import StatCard from '@/shared/components/ui/StatCard'
import Button from '@/shared/components/ui/Button'
import Alert from '@/shared/components/ui/Alert'
import Spinner from '@/shared/components/ui/Spinner'
import UnassignedParcelsTable from '../components/UnassignedParcelsTable'
import AssignCourierModal from '../components/AssignCourierModal'
import styles from './DispatchManagementPage.module.css'

export default function DispatchManagementPage() {
  const [parcels, setParcels] = useState([])
  const [couriers, setCouriers] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [error, setError] = useState(null)
  const [successBanner, setSuccessBanner] = useState(null)

  // Assignment Modal State
  const [selectedParcel, setSelectedParcel] = useState(null)
  const [isModalOpen, setIsModalOpen] = useState(false)

  const loadData = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) {
      setIsRefreshing(true)
    } else {
      setIsLoading(true)
    }
    setError(null)

    try {
      const [unassignedData, couriersData] = await Promise.all([
        getUnassignedParcels(),
        getActiveCouriers(),
      ])

      setParcels(Array.isArray(unassignedData) ? unassignedData : [])
      setCouriers(Array.isArray(couriersData) ? couriersData : [])
    } catch (err) {
      setError(err.message || 'Failed to load dispatch queue from server.')
    } finally {
      setIsLoading(false)
      setIsRefreshing(false)
    }
  }, [])

  useEffect(() => {
    let ignore = false

    async function fetchData() {
      try {
        const [unassignedData, couriersData] = await Promise.all([
          getUnassignedParcels(),
          getActiveCouriers(),
        ])
        if (!ignore) {
          setParcels(Array.isArray(unassignedData) ? unassignedData : [])
          setCouriers(Array.isArray(couriersData) ? couriersData : [])
        }
      } catch (err) {
        if (!ignore) {
          setError(err.message || 'Failed to load dispatch queue from server.')
        }
      } finally {
        if (!ignore) {
          setIsLoading(false)
        }
      }
    }

    fetchData()

    return () => {
      ignore = true
    }
  }, [])

  const handleOpenAssignModal = (parcel) => {
    setSelectedParcel(parcel)
    setIsModalOpen(true)
  }

  const handleCloseAssignModal = () => {
    setIsModalOpen(false)
    setSelectedParcel(null)
  }

  const handleAssignSuccess = (parcel) => {
    setSuccessBanner(
      `Parcel ${parcel.trackingNumber} successfully assigned to courier!`
    )
    loadData(true)

    // Clear success banner after 5 seconds
    setTimeout(() => {
      setSuccessBanner(null)
    }, 5000)
  }

  // Calculate KPIs
  const totalUnassigned = parcels.length
  const totalCouriers = couriers.length
  const expressCount = parcels.filter(
    (p) => p.serviceType === 'EXPRESS' || p.serviceType === 'SAME_DAY'
  ).length

  return (
    <div className={styles.container}>
      <PageHeader
        title="Dispatch Queue"
        subtitle="Review unassigned shipments and allocate active couriers"
        action={
          <Button
            variant="secondary"
            size="sm"
            icon={RefreshCw}
            onClick={() => loadData(true)}
            disabled={isLoading || isRefreshing}
            className={styles.refreshButton}
          >
            {isRefreshing ? 'Refreshing...' : 'Refresh Queue'}
          </Button>
        }
      />

      {/* KPI Stat Cards */}
      <div className={styles.statsGrid}>
        <StatCard
          label="Unassigned Shipments"
          value={totalUnassigned}
          icon={Package}
          tone="orange"
        />
        <StatCard
          label="Active Couriers"
          value={totalCouriers}
          icon={Users}
          tone="success"
        />
        <StatCard
          label="Express & Priority"
          value={expressCount}
          icon={Zap}
          tone="warning"
        />
      </div>

      {/* Success Notification Banner */}
      {successBanner && (
        <div className={styles.successBanner} role="status">
          <CheckCircle2 size={18} />
          <span>{successBanner}</span>
        </div>
      )}

      {/* Error Alert with Retry */}
      {error && (
        <Alert
          tone="error"
          title="Dispatch Queue Error"
          action={
            <Button
              variant="secondary"
              size="sm"
              onClick={() => loadData(false)}
            >
              Retry
            </Button>
          }
        >
          {error}
        </Alert>
      )}

      {/* Main Queue Card */}
      <div className={styles.queueCard}>
        <div className={styles.queueHeader}>
          <h2 className={styles.queueTitle}>
            <Package size={18} color="var(--color-orange, #e06a1f)" />
            <span>Pending Parcels for Assignment</span>
          </h2>
        </div>

        {isLoading ? (
          <div className={styles.loadingWrapper}>
            <Spinner label="Loading unassigned parcel queue..." />
          </div>
        ) : (
          <UnassignedParcelsTable
            parcels={parcels}
            onAssignClick={handleOpenAssignModal}
          />
        )}
      </div>

      {/* Courier Assignment Dialog */}
      <AssignCourierModal
        parcel={selectedParcel}
        couriers={couriers}
        isOpen={isModalOpen}
        onClose={handleCloseAssignModal}
        onAssignSuccess={handleAssignSuccess}
      />
    </div>
  )
}
