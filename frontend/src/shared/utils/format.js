const LOCALE = 'en-LK'
const TIME_ZONE = 'Asia/Colombo'

const timeFormat = new Intl.DateTimeFormat(LOCALE, { timeZone: TIME_ZONE, hour: 'numeric', minute: '2-digit' })
const dateFormat = new Intl.DateTimeFormat(LOCALE, { timeZone: TIME_ZONE, day: 'numeric', month: 'short' })
const fullDateFormat = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TIME_ZONE,
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})
const dayKeyFormat = new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE })

function dayKey(date) {
  return dayKeyFormat.format(date)
}

/** "Today", "Yesterday", "Tomorrow" or "5 Nov", in Sri Lanka time. */
export function formatDay(value) {
  if (!value) return ''
  const date = new Date(value)
  const now = new Date()
  const oneDay = 24 * 60 * 60 * 1000
  const key = dayKey(date)
  if (key === dayKey(now)) return 'Today'
  if (key === dayKey(new Date(now.getTime() - oneDay))) return 'Yesterday'
  if (key === dayKey(new Date(now.getTime() + oneDay))) return 'Tomorrow'
  return dateFormat.format(date)
}

export function formatTime(value) {
  return value ? timeFormat.format(new Date(value)) : ''
}

/** "Today, 11:24 am" */
export function formatDayTime(value) {
  return value ? `${formatDay(value)}, ${formatTime(value)}` : ''
}

/** "Thu, 5 Nov 2026" */
export function formatFullDate(value) {
  return value ? fullDateFormat.format(new Date(value)) : ''
}

/** "Test Customer" -> "TC" */
export function initialsOf(fullName) {
  return (fullName ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

/** "Test Customer" -> "Test" */
export function firstNameOf(fullName) {
  return (fullName ?? '').trim().split(/\s+/)[0] ?? ''
}

export function formatMoney(amount, currency = 'LKR') {
  return `${currency} ${Number(amount).toLocaleString(LOCALE)}`
}
