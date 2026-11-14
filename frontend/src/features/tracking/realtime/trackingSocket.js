import { Client } from '@stomp/stompjs'
import { webSocketUrl } from '@/shared/api/httpClient'

const RECONNECT_DELAY_MS = 5000

/** Same host as the API (VITE_API_BASE_URL), or this page's host when that is empty. */
function brokerUrl() {
  return webSocketUrl('/ws')
}

/**
 * Listens for live status changes of one parcel.
 *
 * @param {string} trackingNumber
 * @param {{ onUpdate: (update: object) => void, onConnectionChange?: (connected: boolean) => void }} handlers
 * @returns {() => void} call to stop listening
 */
export function subscribeToTracking(trackingNumber, { onUpdate, onConnectionChange = () => {} }) {
  const client = new Client({
    brokerURL: brokerUrl(),
    reconnectDelay: RECONNECT_DELAY_MS,
    onConnect: () => {
      onConnectionChange(true)
      client.subscribe(`/topic/tracking/${trackingNumber}`, (message) => {
        onUpdate(JSON.parse(message.body))
      })
    },
    onWebSocketClose: () => onConnectionChange(false),
    onStompError: () => onConnectionChange(false),
  })

  client.activate()
  return () => {
    client.deactivate()
  }
}
