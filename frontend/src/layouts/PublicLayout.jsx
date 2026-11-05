import { Outlet } from 'react-router-dom'
import Logo from '../components/brand/Logo'
import PublicNavbar from '../components/layout/PublicNavbar'
import styles from './PublicLayout.module.css'

export default function PublicLayout() {
  return (
    <div className={styles.layout}>
      <PublicNavbar />
      <main className={styles.main}>
        <Outlet />
      </main>
      <footer className={styles.footer}>
        <Logo tone="dark" />
        <p>© {new Date().getFullYear()} SwiftDrop. Fast, reliable parcel delivery across Sri Lanka.</p>
      </footer>
    </div>
  )
}
