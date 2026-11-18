import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import DispatcherSidebar from './DispatcherSidebar'
import DispatcherTopbar from './DispatcherTopbar'
import styles from './DispatcherLayout.module.css'

/**
 * Dedicated layout shell for Dispatch operations (ROLE_DISPATCHER and ROLE_ADMIN).
 *
 * Provides a tailored operational sidebar and topbar, responsive mobile drawer,
 * and an isolated content view without customer chatbot or widgets.
 */
export default function DispatcherLayout() {
  const [isMenuOpen, setIsMenuOpen] = useState(false)

  return (
    <div className={styles.shell}>
      <DispatcherSidebar
        isOpen={isMenuOpen}
        onClose={() => setIsMenuOpen(false)}
      />
      <div className={styles.content}>
        <DispatcherTopbar
          onOpenMenu={() => setIsMenuOpen(true)}
          isMenuOpen={isMenuOpen}
        />
        <main className={styles.main}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
