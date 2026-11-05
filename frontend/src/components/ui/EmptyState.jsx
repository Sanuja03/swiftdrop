import styles from './EmptyState.module.css'

export default function EmptyState({ icon: Icon, title, children, action }) {
  return (
    <div className={styles.empty}>
      {Icon && (
        <span className={styles.icon}>
          <Icon size={24} aria-hidden="true" />
        </span>
      )}
      <p className={styles.title}>{title}</p>
      {children && <p className={styles.text}>{children}</p>}
      {action}
    </div>
  )
}
