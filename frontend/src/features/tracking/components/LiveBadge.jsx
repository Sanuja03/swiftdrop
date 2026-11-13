import styles from './LiveBadge.module.css'

/** Shows whether live updates are connected. */
export default function LiveBadge({ isLive }) {
  return (
    <span className={`${styles.badge} ${isLive ? styles.live : styles.offline}`}>
      <span className={styles.dot} aria-hidden="true" />
      {isLive ? 'Live updates on' : 'Connecting…'}
    </span>
  )
}
