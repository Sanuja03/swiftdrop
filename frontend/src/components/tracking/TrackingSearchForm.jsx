import { Search } from 'lucide-react'
import { useState } from 'react'
import Button from '../ui/Button'
import styles from './TrackingSearchForm.module.css'

/** Same rule as the backend: 6 to 32 letters, digits or dashes. */
const TRACKING_NUMBER_PATTERN = /^[A-Za-z0-9-]{6,32}$/

/** Tracking number search box. Calls onSearch with a clean, upper-case number. */
export default function TrackingSearchForm({ initialValue = '', onSearch, size = 'lg' }) {
  const [value, setValue] = useState(initialValue)
  const [error, setError] = useState('')

  function handleSubmit(event) {
    event.preventDefault()
    const trackingNumber = value.trim().toUpperCase()
    if (!TRACKING_NUMBER_PATTERN.test(trackingNumber)) {
      setError('Enter a valid tracking number, for example SDP7845621.')
      return
    }
    setError('')
    onSearch(trackingNumber)
  }

  return (
    <form className={`${styles.form} ${styles[size]}`} onSubmit={handleSubmit} noValidate>
      <div className={styles.row}>
        <label className={styles.field}>
          <Search size={18} className={styles.icon} aria-hidden="true" />
          <span className="visually-hidden">Tracking number</span>
          <input
            className={styles.input}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            placeholder="Enter tracking number"
            autoComplete="off"
            spellCheck="false"
            aria-invalid={Boolean(error)}
            aria-describedby={error ? 'tracking-error' : undefined}
          />
        </label>
        <Button type="submit" size={size === 'lg' ? 'lg' : 'md'} className={styles.button}>
          Track Parcel
        </Button>
      </div>
      {error && (
        <p id="tracking-error" className={styles.error} role="alert">
          {error}
        </p>
      )}
    </form>
  )
}
