import { useEffect, useState } from 'react'
import { getMyShipments } from '../api/trackingApi'

/**
 * Loads the signed-in customer's shipments. The backend works out the customer from the
 * JWT, so this never sends a customer id.
 */
export function useMyShipments() {
  const [result, setResult] = useState({ shipments: [], error: null, isLoading: true })

  useEffect(() => {
    const controller = new AbortController()
    getMyShipments({ signal: controller.signal })
      .then((shipments) => setResult({ shipments, error: null, isLoading: false }))
      .catch((error) => {
        if (!controller.signal.aborted) setResult({ shipments: [], error, isLoading: false })
      })
    return () => controller.abort()
  }, [])

  return result
}
