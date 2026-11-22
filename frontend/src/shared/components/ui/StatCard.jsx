import styles from './StatCard.module.css'

/** Headline number with an icon, as on the dashboard. */
export default function StatCard({ label, value, icon: Icon, tone = 'orange' }) {
  return (
    <div className={`${styles.card} ${styles[tone]}`}>
      <span className={styles.icon}>
        <Icon size={22} aria-hidden="true" />
      </span>
      <div>
        <p className={styles.label}>{label}</p>
        <p className={styles.value}>{value}</p>
      </div>
    </div>
  )
}
