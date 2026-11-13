import { CircleAlert, Info } from 'lucide-react'
import styles from './Alert.module.css'

const ICONS = { info: Info, error: CircleAlert }

/** Inline message box. */
export default function Alert({ tone = 'info', title, children, action }) {
  const Icon = ICONS[tone]
  return (
    <div className={`${styles.alert} ${styles[tone]}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon size={18} className={styles.icon} aria-hidden="true" />
      <div className={styles.body}>
        {title && <p className={styles.title}>{title}</p>}
        {children && <div className={styles.text}>{children}</div>}
      </div>
      {action}
    </div>
  )
}
