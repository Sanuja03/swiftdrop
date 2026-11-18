import { X } from 'lucide-react'
import { Link, NavLink } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import Logo from '@/shared/components/brand/Logo'
import { CUSTOMER_NAVIGATION } from './navigation'
import styles from './Sidebar.module.css'

export default function Sidebar({ isOpen, onClose }) {
  return (
    <>
      <aside id="customer-sidebar" className={`${styles.sidebar} ${isOpen ? styles.open : ''}`}>
        <div className={styles.brandRow}>
          <Link to={ROUTES.app} onClick={onClose}>
            <Logo />
          </Link>
          <button type="button" className={styles.close} onClick={onClose} aria-label="Close menu">
            <X size={20} />
          </button>
        </div>
        <nav aria-label="Customer">
          <ul className={styles.nav}>
            {CUSTOMER_NAVIGATION.map(({ label, to, icon: Icon, end }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  end={end}
                  onClick={onClose}
                  className={({ isActive }) => `${styles.link} ${isActive ? styles.active : ''}`}
                >
                  <Icon size={18} aria-hidden="true" />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </aside>
      {isOpen && <button type="button" className={styles.backdrop} onClick={onClose} aria-label="Close menu" />}
    </>
  )
}
