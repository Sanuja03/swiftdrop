import { Bell, LogOut, Menu } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { initialsOf } from '@/shared/utils/format'
import styles from './Topbar.module.css'

export default function Topbar({ onOpenMenu, isMenuOpen }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const fullName = user?.fullName ?? ''

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
        <button type="button" className={styles.iconButton} aria-label="Notifications">
          <Bell size={20} />
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
