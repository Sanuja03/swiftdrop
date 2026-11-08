const MOCK_DELAY_MS = 250

const ZONES = [
  'Colombo',
  'Gampaha',
  'Kalutara',
  'Kandy',
  'Galle',
  'Matara',
  'Kurunegala',
  'Jaffna',
].map((name, index) => ({ id: `ZONE_${index + 1}`, name }))

const BASE_FEES = { STANDARD: 590, EXPRESS: 990, SAME_DAY: 1490 }
const ZONE_FEES = { STANDARD: 250, EXPRESS: 400, SAME_DAY: 600 }

const delay = (value) => new Promise((resolve) => setTimeout(() => resolve(value), MOCK_DELAY_MS))

function calculateFee({ pickupZoneId, dropoffZoneId, weightKg, serviceType }) {
  const baseFee = BASE_FEES[serviceType] ?? BASE_FEES.STANDARD
  const weightCharge = Math.ceil(Number(weightKg) * 100)
  const zoneCharge = pickupZoneId !== dropoffZoneId ? ZONE_FEES[serviceType] ?? ZONE_FEES.STANDARD : 0
  return { baseFee, weightCharge, zoneCharge, totalFee: baseFee + weightCharge + zoneCharge, currency: 'LKR' }
}

export function getZones() {
  return delay(ZONES.map((zone) => ({ ...zone })))
}

export function estimateFee(request) {
  return delay(calculateFee(request))
}

export async function createParcel(request) {
  const fee = calculateFee(request)
  const trackingNumber = `SDP${String(Math.floor(1000000 + Math.random() * 9000000))}`
  return delay({
    parcelId: `PARCEL-${Date.now()}`,
    trackingNumber,
    status: 'CREATED',
    totalFee: fee.totalFee,
    currency: 'LKR',
    createdAt: new Date().toISOString(),
  })
}
