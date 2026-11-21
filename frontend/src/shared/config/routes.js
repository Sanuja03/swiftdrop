/** Every client-side URL in one place, so links never drift from the router. */
export const ROUTES = {
  home: '/',
  track: '/track',
  trackParcel: (trackingNumber) => `/track/${encodeURIComponent(trackingNumber)}`,

  // Customer
  app: '/app',
  shipments: '/app/shipments',
  shipment: (trackingNumber) => `/app/shipments/${encodeURIComponent(trackingNumber)}`,
  createDelivery: '/app/create',
  bookingConfirmation: (trackingNumber) => `/app/bookings/${encodeURIComponent(trackingNumber)}`,
  history: '/app/history',
  settings: '/app/settings',

  // Courier
  courier: '/courier/deliveries',
  courierParcel: (parcelId) => `/courier/deliveries/${encodeURIComponent(parcelId)}`,

  // Dispatcher
  dispatch: '/dispatch',
  dispatchWorkload: '/dispatch/workload',

  // Merchant
  merchant: '/merchant',

  // Admin
  admin: '/admin',
  adminUsers: '/admin/users',
  adminReports: '/admin/reports',
  adminZones: '/admin/zones',
  adminPricing: '/admin/pricing',
  notifications: '/notifications',
}
