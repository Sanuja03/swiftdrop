import { Bell, ChevronDown, Menu } from 'lucide-react'
import { CURRENT_CUSTOMER } from '@/shared/data/currentCustomer'
import styles from './Topbar.module.css'

export default function Topbar({ onOpenMenu, isMenuOpen }) {
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
            {CURRENT_CUSTOMER.initials}
          </span>
          <span className={styles.name}>{CURRENT_CUSTOMER.fullName}</span>
          <ChevronDown size={16} aria-hidden="true" />
        </div>
      </div>
    </header>
  )
}
