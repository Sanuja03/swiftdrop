import { Menu, X } from 'lucide-react'
import { useState } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { ROUTES } from '../../config/routes'
import Logo from '../brand/Logo'
import Button from '../ui/Button'
import styles from './PublicNavbar.module.css'

const LINKS = [
  { label: 'Home', to: ROUTES.home, end: true },
  { label: 'Services', to: `${ROUTES.track}#services` },
  { label: 'Track Parcel', to: ROUTES.track },
  { label: 'About', to: `${ROUTES.track}#about` },
]

export default function PublicNavbar() {
  const [isOpen, setIsOpen] = useState(false)
  const close = () => setIsOpen(false)

  return (
    <header className={styles.navbar}>
      <div className={styles.inner}>
        <Link to={ROUTES.home} onClick={close}>
          <Logo />
        </Link>
        <button
          type="button"
          className={styles.toggle}
          onClick={() => setIsOpen((open) => !open)}
          aria-label={isOpen ? 'Close menu' : 'Open menu'}
          aria-expanded={isOpen}
          aria-controls="public-menu"
        >
          {isOpen ? <X size={22} /> : <Menu size={22} />}
        </button>
        <div id="public-menu" className={`${styles.menu} ${isOpen ? styles.open : ''}`}>
          <nav aria-label="Main">
            <ul className={styles.links}>
              {LINKS.map(({ label, to, end }) => (
                <li key={label}>
                  <NavLink to={to} end={end} onClick={close} className={styles.link}>
                    {label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
          <div className={styles.cta}>
            <Button variant="outlineLight" size="sm" to={ROUTES.app} onClick={close}>
              Login
            </Button>
            <Button size="sm" to={ROUTES.app} onClick={close}>
              Get Started
            </Button>
          </div>
        </div>
      </div>
    </header>
  )
}
