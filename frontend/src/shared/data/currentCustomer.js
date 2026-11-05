/**
 * Stand-in for the signed-in customer until accounts are available.
 * The tracking numbers match the demo parcels the backend seeds in the "dev"
 * profile, so every page shows real, live data from the tracking API.
 * When accounts land, replace this with the current user and their parcels.
 */
export const CURRENT_CUSTOMER = Object.freeze({
  firstName: 'Sanuja',
  fullName: 'Sanuja Perera',
  email: 'sanuja@example.com',
  phone: '+94 77 123 4567',
  initials: 'SP',
})

export const CUSTOMER_TRACKING_NUMBERS = Object.freeze(['SDP7845621', 'SDP7845638', 'SDP7845009'])
