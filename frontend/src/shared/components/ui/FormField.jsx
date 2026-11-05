import { useId } from 'react'
import styles from './FormField.module.css'

/**
 * Label + control. The child receives the generated id via a render function,
 * so the label is always correctly linked to its input.
 */
export default function FormField({ label, icon: Icon, hint, children }) {
  const id = useId()
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {Icon && <Icon size={15} aria-hidden="true" />}
        {label}
      </label>
      {children(id)}
      {hint && <p className={styles.hint}>{hint}</p>}
    </div>
  )
}
