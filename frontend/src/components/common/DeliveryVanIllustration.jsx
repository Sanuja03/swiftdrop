import React from 'react';

export default function DeliveryVanIllustration() {
  return (
    <div style={{ position: 'relative', width: '100%', maxWidth: '520px', display: 'flex', justifyContent: 'center' }}>
      <svg
        viewBox="0 0 540 340"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        style={{ width: '100%', height: 'auto', filter: 'drop-shadow(0 20px 30px rgba(0, 0, 0, 0.45))' }}
      >
        <defs>
          {/* City Skyline Gradient */}
          <linearGradient id="skylineGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#1E3A8A" stopOpacity="0.4" />
            <stop offset="100%" stopColor="#081F38" stopOpacity="0" />
          </linearGradient>

          {/* Van Body Gradients */}
          <linearGradient id="vanBody" x1="0" y1="0" x2="1" y2="0.8">
            <stop offset="0%" stopColor="#FFFFFF" />
            <stop offset="60%" stopColor="#F8FAFC" />
            <stop offset="100%" stopColor="#E2E8F0" />
          </linearGradient>
          <linearGradient id="vanFront" x1="0" y1="0" x2="1" y2="0.5">
            <stop offset="0%" stopColor="#F1F5F9" />
            <stop offset="100%" stopColor="#CBD5E1" />
          </linearGradient>
          <linearGradient id="windshield" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#0F172A" />
            <stop offset="50%" stopColor="#1E293B" />
            <stop offset="100%" stopColor="#0F172A" />
          </linearGradient>
          <linearGradient id="orangeStripe" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#F58220" />
            <stop offset="100%" stopColor="#FF9800" />
          </linearGradient>
          <linearGradient id="headlightGlow" x1="1" y1="0.5" x2="0" y2="0.5">
            <stop offset="0%" stopColor="#FEF08A" stopOpacity="0.8" />
            <stop offset="100%" stopColor="#FEF08A" stopOpacity="0" />
          </linearGradient>

          {/* Wheel Gradients */}
          <radialGradient id="tireGrad" cx="50%" cy="50%" r="50%">
            <stop offset="60%" stopColor="#1E293B" />
            <stop offset="90%" stopColor="#0F172A" />
            <stop offset="100%" stopColor="#020617" />
          </radialGradient>
          <radialGradient id="rimGrad" cx="45%" cy="45%" r="50%">
            <stop offset="0%" stopColor="#E2E8F0" />
            <stop offset="65%" stopColor="#94A3B8" />
            <stop offset="100%" stopColor="#475569" />
          </radialGradient>
        </defs>

        {/* 1. Ambient Background Glow & City Skyline Silhouette */}
        <g opacity="0.6">
          {/* City buildings silhouette */}
          <rect x="40" y="80" width="45" height="150" fill="url(#skylineGrad)" rx="2" />
          <rect x="95" y="50" width="55" height="180" fill="url(#skylineGrad)" rx="2" />
          <rect x="160" y="95" width="40" height="135" fill="url(#skylineGrad)" rx="2" />
          <rect x="360" y="40" width="60" height="190" fill="url(#skylineGrad)" rx="2" />
          <rect x="430" y="70" width="50" height="160" fill="url(#skylineGrad)" rx="2" />
          <rect x="490" y="100" width="45" height="130" fill="url(#skylineGrad)" rx="2" />

          {/* Building glowing windows */}
          <circle cx="115" cy="70" r="1.5" fill="#FEF08A" opacity="0.8" />
          <circle cx="130" cy="70" r="1.5" fill="#93C5FD" opacity="0.7" />
          <circle cx="115" cy="90" r="1.5" fill="#FEF08A" opacity="0.8" />
          <circle cx="130" cy="90" r="1.5" fill="#FEF08A" opacity="0.8" />
          <circle cx="380" cy="65" r="1.5" fill="#FEF08A" opacity="0.9" />
          <circle cx="395" cy="65" r="1.5" fill="#93C5FD" opacity="0.7" />
          <circle cx="380" cy="85" r="1.5" fill="#93C5FD" opacity="0.7" />
          <circle cx="395" cy="85" r="1.5" fill="#FEF08A" opacity="0.8" />
          <circle cx="450" cy="95" r="1.5" fill="#FEF08A" opacity="0.8" />
          <circle cx="465" cy="95" r="1.5" fill="#FEF08A" opacity="0.8" />
        </g>

        {/* 2. Headlight Light Beam Cone */}
        <polygon points="105,218 0,260 0,195 105,212" fill="url(#headlightGlow)" opacity="0.45" />

        {/* 3. Road Surface & Ground Shadows */}
        <ellipse cx="270" cy="285" rx="230" ry="24" fill="#030E1C" opacity="0.8" />
        <ellipse cx="260" cy="282" rx="190" ry="12" fill="#000000" opacity="0.5" />

        {/* 4. White Modern Delivery Van Body (Sprinter/Transit style, 3/4 front view facing left) */}
        
        {/* Main Cargo Box / Side Panel */}
        <path
          d="M 190 105 
             L 470 105 
             Q 485 105 488 120 
             L 492 245 
             Q 490 252 480 252 
             L 420 252 
             Q 420 205 370 205 
             Q 320 205 320 252 
             L 210 252 
             Q 210 205 160 205 
             Q 125 205 115 235 
             L 105 230 
             Q 95 210 105 185 
             L 140 145 
             Q 155 125 180 110 
             Z"
          fill="url(#vanBody)"
          stroke="#E2E8F0"
          strokeWidth="1.5"
        />

        {/* Roof Aerodynamic Deflector Curve */}
        <path
          d="M 175 108 
             Q 240 100 470 105 
             L 485 110 
             L 185 112 
             Z"
          fill="#FFFFFF"
          opacity="0.9"
        />

        {/* Cabin Front Windshield (Angled forward) */}
        <path
          d="M 175 118 
             L 142 152 
             L 180 152 
             L 192 118 
             Z"
          fill="url(#windshield)"
        />
        {/* Windshield Reflection Streak */}
        <path d="M 160 120 L 148 148" stroke="#FFFFFF" strokeWidth="2" opacity="0.4" strokeLinecap="round" />

        {/* Cabin Driver Side Window */}
        <path
          d="M 198 120 
             L 185 152 
             L 245 152 
             L 245 120 
             Z"
          fill="url(#windshield)"
        />
        <path d="M 235 124 L 210 148" stroke="#FFFFFF" strokeWidth="1.5" opacity="0.3" strokeLinecap="round" />

        {/* Side Mirror */}
        <rect x="145" y="152" width="12" height="18" rx="3" fill="#1E293B" />
        <path d="M 157 158 L 163 159" stroke="#94A3B8" strokeWidth="2" />

        {/* Van Front Nose / Hood & Grille (facing forward-left) */}
        <path
          d="M 142 152 
             L 100 178 
             Q 90 185 92 205 
             L 94 225 
             Q 96 235 115 235 
             L 140 235 
             L 142 152 
             Z"
          fill="url(#vanFront)"
          stroke="#CBD5E1"
          strokeWidth="1"
        />

        {/* Front Grille Slats */}
        <rect x="94" y="202" width="8" height="22" rx="2" fill="#0F172A" />
        <line x1="95" y1="207" x2="101" y2="207" stroke="#475569" strokeWidth="1.5" />
        <line x1="95" y1="213" x2="101" y2="213" stroke="#475569" strokeWidth="1.5" />
        <line x1="95" y1="219" x2="101" y2="219" stroke="#475569" strokeWidth="1.5" />

        {/* Front Headlight (High-intensity modern LED) */}
        <path
          d="M 98 186 
             L 115 180 
             L 118 195 
             L 96 198 
             Z"
          fill="#FEF08A"
          stroke="#FFFFFF"
          strokeWidth="1.5"
          filter="drop-shadow(0 0 6px rgba(254, 240, 138, 0.8))"
        />

        {/* Side Sliding Door Seam Lines */}
        <line x1="255" y1="112" x2="255" y2="248" stroke="#CBD5E1" strokeWidth="1.5" />
        <line x1="390" y1="112" x2="390" y2="248" stroke="#CBD5E1" strokeWidth="1.5" />
        <rect x="262" y="165" width="12" height="4" rx="1.5" fill="#64748B" />

        {/* 5. SwiftDrop Brand Livery on Side of Van */}
        {/* Brand Orange Dynamic Curve Ribbon */}
        <path
          d="M 200 185 
             L 485 185 
             L 487 215 
             L 220 215 
             Z"
          fill="url(#orangeStripe)"
        />
        
        {/* SwiftDrop 3D Cube Icon on Cargo Door */}
        <g transform="translate(265, 130) scale(0.9)">
          <rect width="32" height="32" rx="7" fill="#F58220" />
          <path d="M16 7L25 12V22L16 27L7 22V12L16 7Z" fill="#FFFFFF" fillOpacity="0.3" />
          <path d="M16 7L25 12L16 17L7 12L16 7Z" fill="#FFFFFF" />
          <path d="M16 17V27L7 22V12L16 17Z" fill="#FFFFFF" fillOpacity="0.8" />
          <path d="M16 17V27L25 22V12L16 17Z" fill="#FFFFFF" fillOpacity="0.6" />
        </g>

        {/* "SwiftDrop" Text on Van Side */}
        <text
          x="305"
          y="152"
          fontFamily="'Inter', sans-serif"
          fontWeight="800"
          fontSize="22"
          fill="#102A4C"
          letterSpacing="-0.5"
        >
          Swift<tspan fill="#F58220">Drop</tspan>
        </text>

        <text
          x="230"
          y="204"
          fontFamily="'Inter', sans-serif"
          fontWeight="700"
          fontSize="11"
          fill="#FFFFFF"
          letterSpacing="1"
        >
          ISLAND-WIDE EXPRESS COURIER
        </text>

        {/* 6. Front Wheel Assembly */}
        <g>
          {/* Wheel Arch */}
          <path d="M 120 248 Q 160 190 200 248 Z" fill="#0F172A" />
          {/* Outer Tire */}
          <circle cx="160" cy="250" r="36" fill="url(#tireGrad)" />
          {/* Rim */}
          <circle cx="160" cy="250" r="22" fill="url(#rimGrad)" />
          {/* Rim Spokes */}
          <circle cx="160" cy="250" r="8" fill="#1E293B" />
          <line x1="160" y1="230" x2="160" y2="270" stroke="#E2E8F0" strokeWidth="2.5" />
          <line x1="140" y1="250" x2="180" y2="250" stroke="#E2E8F0" strokeWidth="2.5" />
          <line x1="146" y1="236" x2="174" y2="264" stroke="#CBD5E1" strokeWidth="2" />
          <line x1="146" y1="264" x2="174" y2="236" stroke="#CBD5E1" strokeWidth="2" />
        </g>

        {/* 7. Rear Wheel Assembly */}
        <g>
          {/* Wheel Arch */}
          <path d="M 330 248 Q 370 190 410 248 Z" fill="#0F172A" />
          {/* Outer Tire */}
          <circle cx="370" cy="250" r="36" fill="url(#tireGrad)" />
          {/* Rim */}
          <circle cx="370" cy="250" r="22" fill="url(#rimGrad)" />
          {/* Rim Spokes */}
          <circle cx="370" cy="250" r="8" fill="#1E293B" />
          <line x1="370" y1="230" x2="370" y2="270" stroke="#E2E8F0" strokeWidth="2.5" />
          <line x1="350" y1="250" x2="390" y2="250" stroke="#E2E8F0" strokeWidth="2.5" />
          <line x1="356" y1="236" x2="384" y2="264" stroke="#CBD5E1" strokeWidth="2" />
          <line x1="356" y1="264" x2="384" y2="236" stroke="#CBD5E1" strokeWidth="2" />
        </g>

        {/* Bottom Van Chassis Rail */}
        <rect x="200" y="246" width="130" height="6" fill="#1E293B" />
        <rect x="408" y="246" width="75" height="6" fill="#1E293B" />
      </svg>
    </div>
  );
}

