import { useEffect, useState } from 'react'
import { getPublicTracking } from '../api/trackingApi'

/**
 * Loads the current tracking details for a list of tracking numbers.
 * Parcels that cannot be found are skipped; any other failure is reported.
 *
 * @param {string[]} trackingNumbers
 */
export function useShipments(trackingNumbers) {
  const key = trackingNumbers.join(',')
  const [result, setResult] = useState({ key: null, shipments: [], error: null })

  useEffect(() => {
    const controller = new AbortController()
    const numbers = key ? key.split(',') : []

    Promise.allSettled(numbers.map((number) => getPublicTracking(number, { signal: controller.signal }))).then(
      (outcomes) => {
        if (controller.signal.aborted) return
        const shipments = outcomes.filter((o) => o.status === 'fulfilled').map((o) => o.value)
        const failure = outcomes.find((o) => o.status === 'rejected' && !o.reason.isNotFound)
        setResult({ key, shipments, error: failure ? failure.reason : null })
      },
    )
    return () => controller.abort()
  }, [key])

  const isCurrent = result.key === key
  return {
    shipments: isCurrent ? result.shipments : [],
    error: isCurrent ? result.error : null,
    isLoading: !isCurrent,
  }
}
