import { Navigate, useLocation } from 'react-router-dom'
import { ShieldAlert, ArrowLeft, LogOut } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import Spinner from '@/shared/components/ui/Spinner'
import Button from '@/shared/components/ui/Button'
import Card from '@/shared/components/ui/Card'
import Alert from '@/shared/components/ui/Alert'

/**
 * Route guard that grants access exclusively to authenticated users with
 * ROLE_DISPATCHER or ROLE_ADMIN.
 *
 * - If unauthenticated: redirects to /login (preserving return location).
 * - If authenticated with another role (CUSTOMER, COURIER, MERCHANT): displays a clear 403 Access Denied screen.
 * - If loading: renders the standard loading spinner.
 */
export default function DispatcherRoute({ children }) {
  const { isAuthenticated, user, loading, logout } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '60vh',
        }}
      >
        <Spinner label="Verifying authorization..." />
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  const role = user?.role || ''
  const isDispatcherOrAdmin =
    role === 'ROLE_DISPATCHER' ||
    role === 'DISPATCHER' ||
    role === 'ROLE_ADMIN' ||
    role === 'ADMIN'

  if (!isDispatcherOrAdmin) {
    const currentRoleName = user?.role
      ? user.role.replace('ROLE_', '')
      : 'UNASSIGNED'

    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '80vh',
          padding: '24px',
        }}
      >
        <Card
          style={{
            maxWidth: '520px',
            width: '100%',
            padding: '32px',
            textAlign: 'center',
          }}
        >
          <div
            style={{
              display: 'inline-flex',
              padding: '16px',
              borderRadius: '50%',
              background: 'var(--color-error-soft, #fee2e2)',
              color: 'var(--color-error, #dc2626)',
              marginBottom: '16px',
            }}
          >
            <ShieldAlert size={36} aria-hidden="true" />
          </div>

          <h1
            style={{
              fontSize: '22px',
              fontWeight: '700',
              marginBottom: '8px',
              color: 'var(--color-text, #0f172a)',
            }}
          >
            403 - Access Denied
          </h1>

          <p
            style={{
              fontSize: '14px',
              color: 'var(--color-muted, #64748b)',
              marginBottom: '20px',
              lineHeight: '1.5',
            }}
          >
            Dispatcher access required. Your account role is{' '}
            <strong>{currentRoleName}</strong>, which does not have permission
            to view or manage dispatch operations.
          </p>

          <Alert
            tone="error"
            title="Permission Error"
          >
            Only active Dispatchers and Administrators may access dispatch queues and courier workloads.
          </Alert>

          <div
            style={{
              display: 'flex',
              justifyContent: 'center',
              gap: '12px',
              marginTop: '24px',
              flexWrap: 'wrap',
            }}
          >
            <Button variant="primary" to="/app" icon={ArrowLeft}>
              Back to Customer Portal
            </Button>
            <Button
              variant="secondary"
              icon={LogOut}
              onClick={() => logout()}
            >
              Sign Out
            </Button>
          </div>
        </Card>
      </div>
    )
  }

  return children
}
