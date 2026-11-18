import { Link, NavLink } from 'react-router-dom'
import { Truck, Users, ArrowLeft, X } from 'lucide-react'
import Logo from '@/shared/components/brand/Logo'
import styles from './DispatcherSidebar.module.css'

export default function DispatcherSidebar({ isOpen, onClose }) {
  return (
    <>
      <aside
        id="dispatcher-sidebar"
        className={`${styles.sidebar} ${isOpen ? styles.open : ''}`}
        aria-label="Dispatcher Navigation"
      >
        <div className={styles.brandRow}>
          <div className={styles.brandInfo}>
            <Link to="/dispatch" onClick={onClose} style={{ textDecoration: 'none' }}>
              <Logo tone="light" size="md" />
            </Link>
            <span className={styles.portalBadge}>Operations Portal</span>
          </div>
          <button
            type="button"
            className={styles.close}
            onClick={onClose}
            aria-label="Close sidebar menu"
          >
            <X size={20} />
          </button>
        </div>

        <nav className={styles.navSection} aria-label="Dispatch Operations">
          <div className={styles.sectionLabel}>Dispatch Core</div>
          <ul className={styles.nav}>
            <li>
              <NavLink
                to="/dispatch"
                end
                onClick={onClose}
                className={({ isActive }) =>
                  `${styles.link} ${isActive ? styles.active : ''}`
                }
              >
                <Truck size={18} aria-hidden="true" />
                <span>Dispatch Queue</span>
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/dispatch/workload"
                onClick={onClose}
                className={({ isActive }) =>
                  `${styles.link} ${isActive ? styles.active : ''}`
                }
              >
                <Users size={18} aria-hidden="true" />
                <span>Courier Workloads</span>
              </NavLink>
            </li>
          </ul>

          <div className={styles.divider} />

          <div className={styles.sectionLabel}>Switch Workspace</div>
          <ul className={styles.nav}>
            <li>
              <Link
                to="/app"
                onClick={onClose}
                className={`${styles.link} ${styles.switchLink}`}
              >
                <ArrowLeft size={16} aria-hidden="true" />
                <span>Customer View</span>
              </Link>
            </li>
          </ul>
        </nav>
      </aside>

      {isOpen && (
        <button
          type="button"
          className={styles.backdrop}
          onClick={onClose}
          aria-label="Close navigation overlay"
        />
      )}
    </>
  )
}
