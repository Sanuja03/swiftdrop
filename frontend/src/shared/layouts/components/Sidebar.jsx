import { X } from 'lucide-react'
import { Link, NavLink } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import Logo from '@/shared/components/brand/Logo'
import { CUSTOMER_NAVIGATION } from './navigation'
import styles from './Sidebar.module.css'

/**
 * Side navigation. Defaults to the customer menu; other areas (admin) pass their own
 * `items`, `homeTo` and `label`.
 */
export default function Sidebar({ isOpen, onClose, items = CUSTOMER_NAVIGATION, homeTo = ROUTES.app, label = 'Customer' }) {
  return (
    <>
      <aside id="customer-sidebar" className={`${styles.sidebar} ${isOpen ? styles.open : ''}`}>
        <div className={styles.brandRow}>
          <Link to={homeTo} onClick={onClose}>
            <Logo />
          </Link>
          <button type="button" className={styles.close} onClick={onClose} aria-label="Close menu">
            <X size={20} />
          </button>
        </div>
        <nav aria-label={label}>
          <ul className={styles.nav}>
            {items.map(({ label: itemLabel, to, icon: Icon, end, onClick, isActive }) => (
              <li key={to || itemLabel}>
                {onClick ? (
                  <button
                    type="button"
                    onClick={() => {
                      onClick()
                      onClose?.()
                    }}
                    className={`${styles.link} ${isActive ? styles.active : ''}`}
                  >
                    {Icon && <Icon size={18} aria-hidden="true" />}
                    {itemLabel}
                  </button>
                ) : (
                  <NavLink
                    to={to}
                    end={end}
                    onClick={onClose}
                    className={({ isActive: navActive }) => `${styles.link} ${navActive ? styles.active : ''}`}
                  >
                    {Icon && <Icon size={18} aria-hidden="true" />}
                    {itemLabel}
                  </NavLink>
                )}
              </li>
            ))}
          </ul>
        </nav>
      </aside>
      {isOpen && <button type="button" className={styles.backdrop} onClick={onClose} aria-label="Close menu" />}
    </>
  )
}
