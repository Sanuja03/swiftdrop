/** Decorative SwiftDrop van for the tracking hero. */
export default function DeliveryVan({ className }) {
  return (
    <svg className={className} viewBox="0 0 420 220" aria-hidden="true">
      <ellipse cx="215" cy="200" rx="190" ry="10" fill="rgb(0 0 0 / 0.25)" />
      <path d="M30 70q0-22 22-22h196q16 0 26 12l58 66q10 12 30 16l18 4q14 3 14 18v32q0 12-12 12H40q-10 0-10-10z" fill="#F8FAFC" />
      <path d="M248 48h4q14 0 24 12l54 62h-82z" fill="#0F2747" opacity=".9" />
      <path d="M256 60q8 0 14 7l42 50h-56z" fill="#93B4D8" />
      <rect x="30" y="140" width="352" height="16" fill="#E2E8F0" />
      <rect x="30" y="118" width="210" height="8" rx="2" fill="#E06A1F" />
      <g transform="translate(70 66) scale(1.3)">
        <path fill="#E06A1F" d="M16 2 29 8.5v15L16 30 3 23.5v-15z" />
        <path fill="#C25414" d="M16 15v15L3 23.5v-15z" />
        <path fill="#F59E5B" d="M16 2 29 8.5 16 15 3 8.5z" />
      </g>
      <text x="122" y="100" fill="#0F2747" fontFamily="Inter, sans-serif" fontWeight="800" fontSize="26">SwiftDrop</text>
      <rect x="372" y="150" width="18" height="10" rx="3" fill="#F59E0B" />
      {[100, 320].map((cx) => (
        <g key={cx}>
          <circle cx={cx} cy="178" r="24" fill="#1E293B" />
          <circle cx={cx} cy="178" r="11" fill="#CBD5E1" />
        </g>
      ))}
    </svg>
  )
}
