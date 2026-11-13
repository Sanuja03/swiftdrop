import { Link } from 'react-router-dom'
import styles from './Button.module.css'

/**
 * Brand button. Renders a router link when `to` is given, otherwise a <button>.
 * @param {'primary' | 'secondary' | 'ghost' | 'outlineLight'} variant
 */
export default function Button({
  variant = 'primary',
  size = 'md',
  to,
  icon: Icon,
  iconAfter: IconAfter,
  fullWidth = false,
  className = '',
  children,
  ...props
}) {
  const classes = [styles.button, styles[variant], styles[size], fullWidth && styles.fullWidth, className]
    .filter(Boolean)
    .join(' ')

  const content = (
    <>
      {Icon && <Icon size={16} aria-hidden="true" />}
      {children}
      {IconAfter && <IconAfter size={16} aria-hidden="true" />}
    </>
  )

  if (to) {
    return (
      <Link to={to} className={classes} {...props}>
        {content}
      </Link>
    )
  }
  return (
    <button type="button" className={classes} {...props}>
      {content}
    </button>
  )
}
