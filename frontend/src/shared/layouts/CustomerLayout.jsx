import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from '@/shared/layouts/components/Sidebar'
import Topbar from '@/shared/layouts/components/Topbar'
import styles from './CustomerLayout.module.css'

/** Signed-in customer area: sidebar, top bar and the current page. */
export default function CustomerLayout() {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  return (
    <div className={styles.shell}>
      <Sidebar isOpen={isMenuOpen} onClose={() => setIsMenuOpen(false)} />
      <div className={styles.content}>
        <Topbar onOpenMenu={() => setIsMenuOpen(true)} isMenuOpen={isMenuOpen} />
        <main className={styles.main}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
