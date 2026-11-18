import { useState, useEffect, useCallback, useMemo } from 'react'
import {
  Users,
  CheckCircle2,
  Clock,
  AlertTriangle,
  RefreshCw,
  Search,
  X,
  UserCheck,
} from 'lucide-react'
import { getCourierWorkloads } from '../api/dispatchApi'
import PageHeader from '@/shared/components/ui/PageHeader'
import StatCard from '@/shared/components/ui/StatCard'
import Button from '@/shared/components/ui/Button'
import Alert from '@/shared/components/ui/Alert'
import Spinner from '@/shared/components/ui/Spinner'
import WorkloadCard from '../components/WorkloadCard'
import styles from './CourierWorkloadPage.module.css'

export default function CourierWorkloadPage() {
  const [workloads, setWorkloads] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [error, setError] = useState(null)

  // Filter & Search states
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')

  const loadData = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) {
      setIsRefreshing(true)
    } else {
      setIsLoading(true)
    }
    setError(null)

    try {
      const data = await getCourierWorkloads()
      setWorkloads(Array.isArray(data) ? data : [])
    } catch (err) {
      setError(err.message || 'Failed to load courier workloads from server.')
    } finally {
      setIsLoading(false)
      setIsRefreshing(false)
    }
  }, [])

  useEffect(() => {
    let ignore = false

    async function fetchData() {
      try {
        const data = await getCourierWorkloads()
        if (!ignore) {
          setWorkloads(Array.isArray(data) ? data : [])
        }
      } catch (err) {
        if (!ignore) {
          setError(err.message || 'Failed to load courier workloads from server.')
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

  // Summary counts based on all workloads
  const totalCouriers = workloads.length
  const availableCount = useMemo(
    () => workloads.filter((w) => w.workloadStatus === 'AVAILABLE').length,
    [workloads]
  )
  const moderateCount = useMemo(
    () => workloads.filter((w) => w.workloadStatus === 'MODERATE').length,
    [workloads]
  )
  const busyCount = useMemo(
    () => workloads.filter((w) => w.workloadStatus === 'BUSY').length,
    [workloads]
  )

  // Filtered couriers by search query and status filter
  const filteredWorkloads = useMemo(() => {
    const query = searchQuery.trim().toLowerCase()

    return workloads.filter((w) => {
      const matchesSearch =
        !query ||
        (w.courierName && w.courierName.toLowerCase().includes(query)) ||
        (w.courierEmail && w.courierEmail.toLowerCase().includes(query))

      const matchesStatus =
        statusFilter === 'ALL' || w.workloadStatus === statusFilter

      return matchesSearch && matchesStatus
    })
  }, [workloads, searchQuery, statusFilter])

  const handleClearFilters = () => {
    setSearchQuery('')
    setStatusFilter('ALL')
  }

  return (
    <div className={styles.container}>
      <PageHeader
        title="Courier Workloads"
        subtitle="Monitor live delivery distribution and availability across active couriers"
        action={
          <Button
            variant="secondary"
            size="sm"
            icon={RefreshCw}
            onClick={() => loadData(true)}
            disabled={isLoading || isRefreshing}
            className={styles.refreshButton}
          >
            {isRefreshing ? 'Refreshing...' : 'Refresh'}
          </Button>
        }
      />

      {/* KPI Overview Cards */}
      <section className={styles.statsGrid} aria-label="Workload Overview">
        <StatCard
          label="Total Couriers"
          value={totalCouriers}
          tone="orange"
          icon={Users}
        />
        <StatCard
          label="Available (0 active)"
          value={availableCount}
          tone="success"
          icon={CheckCircle2}
        />
        <StatCard
          label="Moderate (1-3 active)"
          value={moderateCount}
          tone="warning"
          icon={Clock}
        />
        <StatCard
          label="Busy (> 3 active)"
          value={busyCount}
          tone="warning"
          icon={AlertTriangle}
        />
      </section>

      {/* Error State Banner */}
      {error && (
        <Alert
          tone="error"
          title="Failed to load courier workloads"
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

      {/* Main Content Area */}
      <div className={styles.contentCard}>
        {/* Controls: Search and Filter Tabs */}
        <div className={styles.controlsBar}>
          <div className={styles.searchBox}>
            <Search size={16} className={styles.searchIcon} aria-hidden="true" />
            <input
              type="text"
              placeholder="Search by courier name or email..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className={styles.searchInput}
              aria-label="Search couriers"
            />
            {searchQuery && (
              <button
                type="button"
                className={styles.clearButton}
                onClick={() => setSearchQuery('')}
                aria-label="Clear search"
              >
                <X size={14} />
              </button>
            )}
          </div>

          <div className={styles.filterTabs} role="tablist" aria-label="Status filters">
            <button
              type="button"
              className={`${styles.filterTab} ${statusFilter === 'ALL' ? styles.activeTab : ''}`}
              onClick={() => setStatusFilter('ALL')}
            >
              All ({totalCouriers})
            </button>
            <button
              type="button"
              className={`${styles.filterTab} ${statusFilter === 'AVAILABLE' ? styles.activeTab : ''}`}
              onClick={() => setStatusFilter('AVAILABLE')}
            >
              Available ({availableCount})
            </button>
            <button
              type="button"
              className={`${styles.filterTab} ${statusFilter === 'MODERATE' ? styles.activeTab : ''}`}
              onClick={() => setStatusFilter('MODERATE')}
            >
              Moderate ({moderateCount})
            </button>
            <button
              type="button"
              className={`${styles.filterTab} ${statusFilter === 'BUSY' ? styles.activeTab : ''}`}
              onClick={() => setStatusFilter('BUSY')}
            >
              Busy ({busyCount})
            </button>
          </div>
        </div>

        {/* Dynamic Display Status Bar */}
        <div className={styles.resultsInfo} data-testid="results-info">
          <span>
            Showing <strong>{filteredWorkloads.length}</strong> of{' '}
            <strong>{totalCouriers}</strong> active couriers
          </span>
        </div>

        {/* Loading Spinner */}
        {isLoading ? (
          <div className={styles.loadingWrapper} data-testid="workload-loading">
            <Spinner text="Loading courier workloads..." />
          </div>
        ) : workloads.length === 0 ? (
          /* Empty State: Zero couriers in system */
          <div className={styles.emptyState} data-testid="workload-empty-state">
            <div className={styles.emptyIconCircle}>
              <UserCheck size={32} />
            </div>
            <h3 className={styles.emptyTitle}>No Active Couriers Found</h3>
            <p className={styles.emptyMessage}>
              There are currently no active couriers registered in the system.
            </p>
          </div>
        ) : filteredWorkloads.length === 0 ? (
          /* Empty State: Filter matched nothing */
          <div className={styles.emptyState} data-testid="workload-no-matches">
            <div className={styles.emptyIconCircle}>
              <Search size={32} />
            </div>
            <h3 className={styles.emptyTitle}>No Couriers Match Your Filter</h3>
            <p className={styles.emptyMessage}>
              No active couriers match &quot;{searchQuery}&quot; with status &quot;{statusFilter}&quot;.
            </p>
            <Button
              variant="secondary"
              size="sm"
              onClick={handleClearFilters}
              className={styles.resetButton}
            >
              Clear Filters
            </Button>
          </div>
        ) : (
          /* Grid of Workload Cards */
          <div className={styles.workloadGrid} data-testid="workload-grid">
            {filteredWorkloads.map((workload) => (
              <WorkloadCard
                key={workload.courierId || workload.courierEmail}
                workload={workload}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
