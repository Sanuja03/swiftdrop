function renderRows(id, rows, columns) {
  const target = document.getElementById(id)
  target.replaceChildren()
  for (const row of rows) {
    const tr = document.createElement('tr')
    for (const column of columns) {
      const td = document.createElement('td')
      td.textContent = row[column] == null ? '—' : String(row[column])
      tr.append(td)
    }
    target.append(tr)
  }
  if (!rows.length) {
    const tr = document.createElement('tr'), td = document.createElement('td')
    td.colSpan = columns.length; td.textContent = 'No verified deliveries yet.'; tr.append(td); target.append(tr)
  }
}

async function refresh() {
  const status = document.getElementById('status')
  try {
    const [health, history] = await Promise.all([fetch('/health'), fetch('/events')])
    if (!health.ok || !history.ok) throw new Error('Receiver unavailable')
    const config = await health.json(), data = await history.json()
    renderRows('events', data.events, ['receivedAt', 'type', 'trackingNumber', 'status', 'id'])
    renderRows('attempts', data.attempts, ['receivedAt', 'id', 'attempt', 'status', 'duplicate'])
    status.textContent = `Receiver ready. First ${config.failFirst} attempt(s) per new event return 503. Showing up to ${config.maxEvents} records.`
  } catch { status.textContent = 'Cannot reach the local receiver. Check its terminal and port.' }
}
document.getElementById('refresh').addEventListener('click', refresh)
refresh()
setInterval(refresh, 3000)
