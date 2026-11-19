import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import Sidebar from '@/shared/layouts/components/Sidebar'
import Topbar from '@/shared/layouts/components/Topbar'
import { ADMIN_NAVIGATION } from '@/shared/layouts/components/navigation'
import styles from './CustomerLayout.module.css'

/** Signed-in admin area: the same shell as the customer area, with the admin menu. */
export default function AdminLayout() {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  return (
    <div className={styles.shell}>
      <Sidebar
        isOpen={isMenuOpen}
        onClose={() => setIsMenuOpen(false)}
        items={ADMIN_NAVIGATION}
        homeTo={ROUTES.adminZones}
        label="Admin"
      />
      <div className={styles.content}>
        <Topbar onOpenMenu={() => setIsMenuOpen(true)} isMenuOpen={isMenuOpen} />
        <main className={styles.main}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
