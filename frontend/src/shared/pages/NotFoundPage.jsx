import { PackageSearch } from 'lucide-react'
import Button from '@/shared/components/ui/Button'
import EmptyState from '@/shared/components/ui/EmptyState'
import { ROUTES } from '@/shared/config/routes'

export default function NotFoundPage() {
  return (
    <div style={{ padding: '80px 16px' }}>
      <EmptyState
        icon={PackageSearch}
        title="Page not found"
        action={<Button to={ROUTES.track}>Track a parcel</Button>}
      >
        The page you are looking for does not exist or has moved.
      </EmptyState>
    </div>
  )
}
