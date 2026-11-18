import { useRef, useState } from 'react'
import { request } from '@/shared/api/httpClient'
import Button from '@/shared/components/ui/Button'
import { statusLabel } from '@/shared/domain/parcelStatus'
import styles from './CourierPage.module.css'

const NEXT_STATUS = {
  CREATED: 'PICKED_UP',
  PICKED_UP: 'IN_TRANSIT',
  IN_TRANSIT: 'OUT_FOR_DELIVERY',
}

export default function DeliveryActions({ parcel, onStatusChange }) {
  const [action, setAction] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [proofSaved, setProofSaved] = useState(parcel.hasSavedProof === true)
  const path = `/courier/parcels/${encodeURIComponent(parcel.parcelId)}`
  const next = NEXT_STATUS[parcel.status]
  const canFinish = parcel.status === 'OUT_FOR_DELIVERY'
  const delivered = parcel.status === 'DELIVERED'

  async function run(operation) {
    setPending(true)
    setError('')
    setNotice('')
    try {
      await operation()
      setAction('')
    } catch (failure) {
      setError(failure.message)
    } finally {
      setPending(false)
    }
  }

  function choose(value) {
    setAction(value)
    setError('')
    setNotice('')
  }

  async function saveStatus(status, note) {
    await request(`${path}/status`, { method: 'PATCH', json: { status, note } })
    onStatusChange(status)
    setNotice(`Status updated to ${statusLabel(status)}.`)
  }

  function changeStatus(status, note) {
    return run(() => saveStatus(status, note))
  }

  function markDelivered() {
    if (proofSaved) return changeStatus('DELIVERED')
    choose('proof')
  }

  function submitProof(event) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const correction = action === 'correction'
    const root = `${path}/proof${correction ? '/corrections' : ''}`
    const changeReason = data.get('changeReason')?.trim()
    const recipientName = data.get('recipientName')?.trim()
    const photo = data.get('photo')

    return run(async () => {
      if (correction && !changeReason) throw new Error('Enter a correction reason.')
      if (!recipientName && !photo?.size) throw new Error('Enter a recipient name or select a photo.')
      if (photo?.size) {
        if (photo.size > 5 * 1024 * 1024) throw new Error('Choose a photo up to 5 MB.')
        const body = new FormData()
        body.set('photo', photo)
        if (recipientName) body.set('recipientName', recipientName)
        if (correction) body.set('changeReason', changeReason)
        await request(`${root}/photo`, { method: 'POST', body })
      } else {
        await request(root, { json: { recipientName, changeReason } })
      }
      if (correction) {
        setNotice('Proof correction saved.')
        return
      }

      setProofSaved(true)
      setAction('')
      try {
        await saveStatus('DELIVERED')
      } catch (failure) {
        throw new Error(`Proof saved. Mark delivered did not complete. You can retry the status update. ${failure.message}`, { cause: failure })
      }
    })
  }

  if (!next && !canFinish && !delivered && !notice) return null

  return (
    <section className={styles.card} aria-labelledby="delivery-actions">
      <h2 id="delivery-actions">{delivered ? 'Delivery proof' : 'Update delivery'}</h2>
      {error && <p className={styles.error} role="alert">{error}</p>}
      {notice && <p className={styles.success} role="status">{notice}</p>}
      <div className={styles.buttons}>
        {next && <Button disabled={pending} onClick={() => changeStatus(next)}>{statusLabel(next)}</Button>}
        {canFinish && <>
          <Button disabled={pending} onClick={markDelivered}>Mark delivered</Button>
          <Button variant="secondary" disabled={pending} onClick={() => choose('failed')}>Mark failed</Button>
        </>}
        {delivered && <Button variant="secondary" disabled={pending} onClick={() => choose('correction')}>Correct proof</Button>}
      </div>
      {action === 'failed' && (
        <form className={styles.form} onSubmit={(event) => {
          event.preventDefault()
          const reason = new FormData(event.currentTarget).get('reason').trim()
          if (!reason) { setError('Enter the reason the delivery failed.'); return }
          changeStatus('FAILED', reason)
        }}>
          <label>Failure reason<textarea name="reason" required maxLength={500} rows={3} /></label>
          <div className={styles.buttons}>
            <Button type="submit" disabled={pending}>Save failed delivery</Button>
            <Button variant="secondary" disabled={pending} onClick={() => choose('')}>Cancel</Button>
          </div>
        </form>
      )}
      {(action === 'proof' || action === 'correction') && (
        <form key={action} className={styles.form} onSubmit={submitProof}>
          <h3>{action === 'proof' ? 'Add delivery proof' : 'Correct delivery proof'}</h3>
          <p>Enter a recipient name, upload a photo, or provide both.</p>
          <label>Recipient name<input name="recipientName" maxLength={150} /></label>
          <ProofPhotoField disabled={pending} />
          {action === 'correction' && <label>Correction reason<textarea name="changeReason" required maxLength={500} rows={3} /></label>}
          <div className={styles.buttons}>
            <Button type="submit" disabled={pending}>
              {pending ? 'Saving…' : action === 'proof' ? 'Save proof & deliver' : 'Save correction'}
            </Button>
            <Button variant="secondary" disabled={pending} onClick={() => choose('')}>Cancel</Button>
          </div>
        </form>
      )}
    </section>
  )
}

function ProofPhotoField({ disabled }) {
  const inputRef = useRef(null)
  const [filename, setFilename] = useState('')

  function removePhoto() {
    inputRef.current.value = ''
    setFilename('')
    inputRef.current.focus()
  }

  return (
    <div className={styles.photoField}>
      <label>
        Delivery photo <small>JPEG or PNG, up to 5 MB</small>
        <input
          ref={inputRef}
          name="photo"
          type="file"
          accept="image/jpeg,image/png"
          disabled={disabled}
          onChange={(event) => setFilename(event.target.files[0]?.name ?? '')}
        />
      </label>
      {filename && (
        <Button variant="secondary" disabled={disabled} onClick={removePhoto}>
          Remove photo
        </Button>
      )}
    </div>
  )
}
