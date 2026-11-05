import styles from './Spinner.module.css'

export default function Spinner({ label = 'Loading' }) {
  return (
    <div className={styles.wrapper} role="status">
      <span className={styles.spinner} aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}
