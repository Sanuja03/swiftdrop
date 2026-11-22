import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bell } from 'lucide-react'
import { getUnreadCount } from '../services/notificationApi'
import styles from './NotificationBell.module.css'

export default function NotificationBell() {
  const [count, setCount] = useState(0)
  useEffect(() => {
    let active = true
    async function refresh() {
      if (document.hidden) return
      try {
        const value = await getUnreadCount()
        if (active) setCount(Number(value) || 0)
      } catch { /* Keep the bell usable when the count is temporarily unavailable. */ }
    }
    refresh()
    const timer = window.setInterval(refresh, 60000)
    window.addEventListener('focus', refresh)
    window.addEventListener('swiftdrop:notifications-changed', refresh)
    return () => {
      active = false
      window.clearInterval(timer)
      window.removeEventListener('focus', refresh)
      window.removeEventListener('swiftdrop:notifications-changed', refresh)
    }
  }, [])
  return <Link to="/notifications" className={styles.bell} aria-label={count ? `Notifications, ${count} unread` : 'Notifications'}>
    <Bell size={21} aria-hidden="true" />
    {count > 0 && <span className={styles.count}>{count > 99 ? '99+' : count}</span>}
  </Link>
}
