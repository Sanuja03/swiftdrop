/**
 * Parcel lifecycle as shown to customers. Mirrors ParcelStatus in the backend:
 * CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED | FAILED
 */
export const PARCEL_STATUS = Object.freeze({
  CREATED: 'CREATED',
  PICKED_UP: 'PICKED_UP',
  IN_TRANSIT: 'IN_TRANSIT',
  OUT_FOR_DELIVERY: 'OUT_FOR_DELIVERY',
  DELIVERED: 'DELIVERED',
  FAILED: 'FAILED',
})

const STATUS_DETAILS = {
  CREATED: { label: 'Order Created', tone: 'neutral' },
  PICKED_UP: { label: 'Picked Up', tone: 'info' },
  IN_TRANSIT: { label: 'In Transit', tone: 'info' },
  OUT_FOR_DELIVERY: { label: 'Out for Delivery', tone: 'warning' },
  DELIVERED: { label: 'Delivered', tone: 'success' },
  FAILED: { label: 'Delivery Failed', tone: 'error' },
}

/** The steps every successful delivery passes through, in order. */
export const JOURNEY = [
  PARCEL_STATUS.CREATED,
  PARCEL_STATUS.PICKED_UP,
  PARCEL_STATUS.IN_TRANSIT,
  PARCEL_STATUS.OUT_FOR_DELIVERY,
  PARCEL_STATUS.DELIVERED,
]

export function statusLabel(status) {
  return STATUS_DETAILS[status]?.label ?? status
}

export function statusTone(status) {
  return STATUS_DETAILS[status]?.tone ?? 'neutral'
}

export function isFinished(status) {
  return status === PARCEL_STATUS.DELIVERED || status === PARCEL_STATUS.FAILED
}

export function isActive(status) {
  return !isFinished(status)
}

/** 0 when just created, 1 when delivered. A failed parcel stays where it stopped. */
export function journeyProgress(status) {
  const index = JOURNEY.indexOf(status === PARCEL_STATUS.FAILED ? PARCEL_STATUS.OUT_FOR_DELIVERY : status)
  return Math.max(index, 0) / (JOURNEY.length - 1)
}

/**
 * Builds the stepper: each journey step with its state and the time it happened.
 * A failed parcel shows "Delivery Failed" in place of the final step.
 *
 * @returns {{ status: string, label: string, state: 'done' | 'current' | 'upcoming' | 'failed', changedAt?: string }[]}
 */
export function buildJourneySteps(currentStatus, timeline = []) {
  const reachedAt = new Map(timeline.map((entry) => [entry.status, entry.changedAt]))
  const failed = currentStatus === PARCEL_STATUS.FAILED
  const steps = failed ? [...JOURNEY.slice(0, -1), PARCEL_STATUS.FAILED] : JOURNEY
  const currentIndex = steps.indexOf(currentStatus)

  return steps.map((status, index) => {
    let state = 'upcoming'
    if (index < currentIndex) state = 'done'
    else if (index === currentIndex) state = failed ? 'failed' : status === PARCEL_STATUS.DELIVERED ? 'done' : 'current'
    return { status, label: statusLabel(status), state, changedAt: reachedAt.get(status) }
  })
}
