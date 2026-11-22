import NotificationBell from '@/features/notifications/components/NotificationBell'
import { Menu, LogOut } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import styles from './DispatcherTopbar.module.css'

export default function DispatcherTopbar({ onOpenMenu, isMenuOpen }) {
  const { user, logout } = useAuth()

  const displayName = user?.fullName || user?.email || 'Authorized User'
  const initials = displayName
    .split(' ')
    .map((word) => word[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase() || 'OP'

  const role = user?.role || ''
  const isAdmin = role === 'ROLE_ADMIN' || role === 'ADMIN'
  const roleLabel = isAdmin ? 'ADMIN' : 'DISPATCHER'

  const badgeClass = isAdmin
    ? `${styles.roleBadge} ${styles.adminBadge}`
    : `${styles.roleBadge} ${styles.dispatcherBadge}`

  return (
    <header className={styles.topbar} aria-label="Dispatcher Header">
      <div className={styles.leftGroup}>
        <button
          type="button"
          className={styles.menuButton}
          onClick={onOpenMenu}
          aria-label="Open sidebar menu"
          aria-expanded={isMenuOpen}
          aria-controls="dispatcher-sidebar"
        >
          <Menu size={22} />
        </button>
        <span className={styles.portalTitle}>Dispatch Operations</span>
      </div>

      <div className={styles.userGroup}>
        <NotificationBell />
        <div className={styles.account}>
          <span className={styles.avatar} aria-hidden="true">
            {initials}
          </span>
          <div className={styles.userInfo}>
            <span className={styles.name}>{displayName}</span>
            <span className={badgeClass}>{roleLabel}</span>
          </div>
        </div>

        <button
          type="button"
          className={styles.logoutButton}
          onClick={() => logout()}
          title="Sign Out of SwiftDrop"
        >
          <LogOut size={16} aria-hidden="true" />
          <span>Sign Out</span>
        </button>
      </div>
    </header>
  )
}
