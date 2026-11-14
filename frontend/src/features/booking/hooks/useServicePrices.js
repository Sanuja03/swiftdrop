import { useEffect, useState } from 'react'
import { getServicePrices } from '../api/bookingApi'

/**
 * Starting prices from the pricing service, keyed by service type:
 * { STANDARD: { fromPrice, currency }, ... }. Empty until loaded or if loading fails,
 * so callers simply leave the price out instead of showing a made-up one.
 */
export function useServicePrices() {
  const [prices, setPrices] = useState({})

  useEffect(() => {
    const controller = new AbortController()
    getServicePrices({ signal: controller.signal })
      .then((items) => setPrices(Object.fromEntries(items.map(({ serviceType, ...price }) => [serviceType, price]))))
      .catch(() => {
        // Prices are a nice-to-have on these pages; the booking flow shows the real estimate.
      })
    return () => controller.abort()
  }, [])

  return prices
}
