/** Most recently updated shipments first. */
export function sortByLatestUpdate(shipments) {
  return [...shipments].sort((a, b) => new Date(b.lastUpdatedAt) - new Date(a.lastUpdatedAt))
}
