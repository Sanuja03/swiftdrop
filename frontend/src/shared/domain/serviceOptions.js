/**
 * Delivery services customers can choose. Values match ServiceType in the backend.
 * Prices are not kept here: starting prices come from GET /api/v1/pricing/services
 * (useServicePrices) and the real fee from the pricing service's estimate.
 */
export const SERVICE_OPTIONS = [
  {
    value: 'STANDARD',
    name: 'Standard',
    description: '1 to 3 working days',
  },
  {
    value: 'EXPRESS',
    name: 'Express',
    description: 'Next working day',
  },
  {
    value: 'SAME_DAY',
    name: 'Same Day',
    description: 'Delivered within hours',
  },
]

export function findServiceOption(value) {
  return SERVICE_OPTIONS.find((option) => option.value === value) ?? SERVICE_OPTIONS[0]
}
