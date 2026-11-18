
export default function Logo({ size = 'medium', dark = false }) {
  const isLarge = size === 'large';
  const isSmall = size === 'small';

  const iconSize = isLarge ? 38 : isSmall ? 24 : 30;
  const textSize = isLarge ? '26px' : isSmall ? '18px' : '22px';

  return (
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: '10px', userSelect: 'none' }}>
      {/* SwiftDrop Isometric Parcel Icon */}
      <svg
        width={iconSize}
        height={iconSize}
        viewBox="0 0 40 40"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <rect width="40" height="40" rx="9" fill="#F58220" />
        {/* Isometric 3D box folds in white */}
        <path
          d="M20 9L31 15.5V28.5L20 35L9 28.5V15.5L20 9Z"
          fill="#FFFFFF"
          fillOpacity="0.25"
        />
        <path
          d="M20 9L31 15.5L20 22L9 15.5L20 9Z"
          fill="#FFFFFF"
        />
        <path
          d="M20 22V35L9 28.5V15.5L20 22Z"
          fill="#FFFFFF"
          fillOpacity="0.8"
        />
        <path
          d="M20 22V35L31 28.5V15.5L20 22Z"
          fill="#FFFFFF"
          fillOpacity="0.6"
        />
        {/* Swift drop arrow on top face */}
        <path
          d="M17 14L23 17.5M23 14L17 17.5"
          stroke="#F58220"
          strokeWidth="1.5"
          strokeLinecap="round"
        />
      </svg>

      <span
        style={{
          fontFamily: "'Inter', sans-serif",
          fontWeight: 700,
          fontSize: textSize,
          letterSpacing: '-0.5px',
          color: dark ? '#FFFFFF' : '#102A4C'
        }}
      >
        Swift<span style={{ color: '#F58220' }}>Drop</span>
      </span>
    </div>
  );
}

