import { PackageSearch, WifiOff } from 'lucide-react'
import Card from '@/shared/components/ui/Card'
import EmptyState from '@/shared/components/ui/EmptyState'
import Spinner from '@/shared/components/ui/Spinner'

/** Loading and error states shared by every tracking view. */
export default function TrackingFeedback({ isLoading, error, trackingNumber }) {
  if (isLoading) {
    return (
      <Card>
        <Spinner label={`Finding parcel ${trackingNumber}…`} />
      </Card>
    )
  }
  if (error?.isNotFound) {
    return (
      <Card>
        <EmptyState icon={PackageSearch} title="We couldn't find that parcel">
          Check the tracking number {trackingNumber} on your booking confirmation and try again.
        </EmptyState>
      </Card>
    )
  }
  if (error) {
    return (
      <Card>
        <EmptyState icon={WifiOff} title="Tracking is unavailable right now">
          {error.message}
        </EmptyState>
      </Card>
    )
  }
  return null
}
