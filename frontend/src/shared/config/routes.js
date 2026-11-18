/** Every client-side URL in one place, so links never drift from the router. */
export const ROUTES = {
  home: '/',
  track: '/track',
  trackParcel: (trackingNumber) => `/track/${encodeURIComponent(trackingNumber)}`,
  app: '/app',
  courier: '/courier/deliveries',
  courierParcel: (parcelId) => `/courier/deliveries/${encodeURIComponent(parcelId)}`,
  shipments: '/app/shipments',
  shipment: (trackingNumber) => `/app/shipments/${encodeURIComponent(trackingNumber)}`,
  createDelivery: '/app/create',
  bookingConfirmation: (trackingNumber) => `/app/bookings/${encodeURIComponent(trackingNumber)}`,
  history: '/app/history',
  settings: '/app/settings',
  dispatch: '/dispatch',
  dispatchWorkload: '/dispatch/workload',
}
