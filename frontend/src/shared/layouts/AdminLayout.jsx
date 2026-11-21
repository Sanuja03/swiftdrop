import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import Sidebar from '@/shared/layouts/components/Sidebar'
import Topbar from '@/shared/layouts/components/Topbar'
import { ADMIN_NAVIGATION } from '@/shared/layouts/components/navigation'
import styles from './CustomerLayout.module.css'
import { useAuth } from '@/context/AuthContext'

/** Signed-in admin area: the same shell as the customer area, with the admin menu. */
export default function AdminLayout() {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const { user } = useAuth()
  const role = user?.role?.replace(/^ROLE_/, '')
  const navigation = ADMIN_NAVIGATION.filter((item) => !item.roles || item.roles.includes(role))
  return (
    <div className={styles.shell}>
      <Sidebar
        isOpen={isMenuOpen}
        onClose={() => setIsMenuOpen(false)}
        items={navigation}
        homeTo={ROUTES.admin}
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
