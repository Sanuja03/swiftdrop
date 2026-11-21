import { Bell, LogOut, Menu } from 'lucide-react'
import { useEffect, useState } from 'react'
import { getUnreadCount } from '@/features/notifications/services/notificationApi'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { initialsOf } from '@/shared/utils/format'
import styles from './Topbar.module.css'

export default function Topbar({ onOpenMenu, isMenuOpen, onNotificationClick, notificationAriaLabel }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const fullName = user?.fullName ?? ''
  const [unreadCount, setUnreadCount] = useState(0)

  useEffect(() => {
    let active = true
    async function refreshCount() {
      try {
        const count = await getUnreadCount()
        if (active) setUnreadCount(count)
      } catch {
        // The notifications page displays API errors and offers a refresh.
      }
    }
    refreshCount()
    window.addEventListener('focus', refreshCount)
    window.addEventListener('swiftdrop:notifications-changed', refreshCount)
    return () => {
      active = false
      window.removeEventListener('focus', refreshCount)
      window.removeEventListener('swiftdrop:notifications-changed', refreshCount)
    }
  }, [user?.id])

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <header className={styles.topbar}>
      <button
        type="button"
        className={styles.menuButton}
        onClick={onOpenMenu}
        aria-label="Open menu"
        aria-expanded={isMenuOpen}
        aria-controls="customer-sidebar"
      >
        <Menu size={22} />
      </button>
      <div className={styles.actions}>
        <button
          type="button"
          className={styles.iconButton}
          onClick={onNotificationClick || (() => navigate('/notifications'))}
          aria-label={notificationAriaLabel || `Notifications${unreadCount > 0 ? `, ${unreadCount} unread` : ""}`}
          title="View notifications"
        >
          <Bell size={20} aria-hidden="true" />
          {unreadCount > 0 && <span className={styles.unreadBadge} aria-hidden="true">{unreadCount > 99 ? '99+' : unreadCount}</span>}
        </button>
        <div className={styles.account}>
          <span className={styles.avatar} aria-hidden="true">
            {initialsOf(fullName)}
          </span>
          <span className={styles.name}>{fullName}</span>
        </div>
        <button type="button" className={styles.iconButton} onClick={handleLogout} aria-label="Sign out" title="Sign out">
          <LogOut size={20} />
        </button>
      </div>
    </header>
  )
}
