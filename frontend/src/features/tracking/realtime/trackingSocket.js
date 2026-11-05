import { Client } from '@stomp/stompjs'

const RECONNECT_DELAY_MS = 5000

function brokerUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}/ws`
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
