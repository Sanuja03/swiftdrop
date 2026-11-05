import styles from './Card.module.css'

export default function Card({ as: Tag = 'section', className = '', children, ...props }) {
  return (
    <Tag className={`${styles.card} ${className}`} {...props}>
      {children}
    </Tag>
  )
}

/** Card title row with an optional icon and an action on the right (e.g. "View All"). */
export function CardHeader({ title, icon: Icon, action, as: Heading = 'h2' }) {
  return (
    <header className={styles.header}>
      <Heading className={styles.title}>
        {Icon && <Icon size={18} className={styles.icon} aria-hidden="true" />}
        {title}
      </Heading>
      {action}
    </header>
  )
}
