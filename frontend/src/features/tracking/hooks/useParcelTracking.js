import { useEffect, useState } from 'react'
import { getPublicTracking } from '../api/trackingApi'
import { subscribeToTracking } from '../realtime/trackingSocket'

/**
 * Loads a parcel's public tracking details and keeps them up to date live:
 * each status change pushed over the WebSocket triggers a fresh load.
 *
 * @param {string | undefined} trackingNumber
 */
export function useParcelTracking(trackingNumber) {
  const [result, setResult] = useState({ key: null, data: null, error: null })
  const [refreshCount, setRefreshCount] = useState(0)
  const [isLive, setIsLive] = useState(false)
  const [lastUpdate, setLastUpdate] = useState(null)

  useEffect(() => {
    if (!trackingNumber) return undefined
    const controller = new AbortController()
    getPublicTracking(trackingNumber, { signal: controller.signal })
      .then((data) => setResult({ key: trackingNumber, data, error: null }))
      .catch((error) => {
        if (!controller.signal.aborted) setResult({ key: trackingNumber, data: null, error })
      })
    return () => controller.abort()
  }, [trackingNumber, refreshCount])

  useEffect(() => {
    if (!trackingNumber) return undefined
    return subscribeToTracking(trackingNumber.toUpperCase(), {
      onUpdate: (update) => {
        setLastUpdate(update)
        setRefreshCount((count) => count + 1)
      },
      onConnectionChange: setIsLive,
    })
  }, [trackingNumber])

  const isCurrent = result.key === trackingNumber
  return {
    data: isCurrent ? result.data : null,
    error: isCurrent ? result.error : null,
    isLoading: Boolean(trackingNumber) && !isCurrent,
    isLive,
    lastUpdate,
  }
}
