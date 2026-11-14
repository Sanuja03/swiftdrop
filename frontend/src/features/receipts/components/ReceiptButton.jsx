import { Download } from 'lucide-react'
import { useState } from 'react'
import { getReceipt } from '../api/receiptApi'
import { saveBlob } from '@/shared/utils/download'
import Button from '@/shared/components/ui/Button'
import styles from './ReceiptButton.module.css'

function errorMessage(error) {
  return error.isUnauthorized ? 'Sign in to download receipts.' : 'Could not download the receipt. Try again.'
}

/** Downloads a parcel's PDF receipt. */
export default function ReceiptButton({ trackingNumber }) {
  const [isDownloading, setIsDownloading] = useState(false)
  const [error, setError] = useState('')

  async function handleClick() {
    setIsDownloading(true)
    setError('')
    try {
      const pdf = await getReceipt(trackingNumber)
      saveBlob(pdf, `SwiftDrop-receipt-${trackingNumber}.pdf`)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setIsDownloading(false)
    }
  }

  return (
    <div className={styles.wrapper}>
      <Button variant="secondary" size="sm" icon={Download} onClick={handleClick} disabled={isDownloading}>
        {isDownloading ? 'Preparing…' : 'Receipt'}
      </Button>
      {error && (
        <span className={styles.error} role="alert">
          {error}
        </span>
      )}
    </div>
  )
}
