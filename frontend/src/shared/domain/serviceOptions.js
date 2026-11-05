/**
 * Delivery services customers can choose. Values match ServiceType in the backend.
 * Prices are display-only starting prices; the real fee comes from the pricing service.
 */
export const SERVICE_OPTIONS = [
  {
    value: 'STANDARD',
    name: 'Standard',
    description: '1 to 3 working days',
    fromPrice: 590,
  },
  {
    value: 'EXPRESS',
    name: 'Express',
    description: 'Next working day',
    fromPrice: 990,
  },
  {
    value: 'SAME_DAY',
    name: 'Same Day',
    description: 'Delivered within hours',
    fromPrice: 1490,
  },
]

export function findServiceOption(value) {
  return SERVICE_OPTIONS.find((option) => option.value === value) ?? SERVICE_OPTIONS[0]
}
