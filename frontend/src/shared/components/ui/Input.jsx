import styles from './FormField.module.css'

export function Input({ className = '', ...props }) {
  return <input className={`${styles.input} ${className}`} {...props} />
}

export function Select({ className = '', children, ...props }) {
  return (
    <select className={`${styles.input} ${className}`} {...props}>
      {children}
    </select>
  )
}
