import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { 
  Search, 
  Truck, 
  MapPin, 
  Package, 
  CheckCircle2, 
  Clock, 
  Calendar, 
  ArrowRight, 
  ShieldCheck, 
  ChevronRight, 
  Layers, 
  CreditCard, 
  Navigation,
  FileText,
  PhoneCall,
  Sparkles
} from 'lucide-react';
import Logo from '../components/common/Logo';
import DeliveryVanIllustration from '../components/common/DeliveryVanIllustration';
import './LandingPage.css';

export default function LandingPage() {
  const [trackingInput, setTrackingInput] = useState('');
  const [currentTrackingNo, setCurrentTrackingNo] = useState('SDP7845621');
  const [searchNotification, setSearchNotification] = useState('');

  const handleTrackSubmit = (e) => {
    e.preventDefault();
    const query = trackingInput.trim().toUpperCase();
    if (!query) return;
    setCurrentTrackingNo(query);
    setSearchNotification(`Showing live status for parcel ${query}`);
    setTimeout(() => setSearchNotification(''), 4000);
  };

  return (
    <div className="landing-page-root">
      {/* 1. Dark Navy Navbar (as specified in SwiftDrop Colour Guide) */}
      <header className="landing-nav-bar">
        <div className="landing-nav-container">
          <Link to="/" className="landing-brand-link">
            <Logo size="medium" dark={true} />
          </Link>

          <nav className="landing-nav-menu">
            <a href="#hero" className="nav-item active">Home</a>
            <a href="#services" className="nav-item">Services</a>
            <a href="#tracking-dashboard" className="nav-item">Track Parcel</a>
            <a href="#about" className="nav-item">About</a>
          </nav>

          <div className="landing-nav-cta">
            <Link to="/login" className="btn-nav-login">Login</Link>
            <Link to="/signup" className="btn-nav-get-started">Get Started</Link>
          </div>
        </div>
      </header>

      {/* 2. Hero Section (matching bottom-left 'Track Parcel' design template) */}
      <section id="hero" className="landing-hero-section">
        <div className="landing-hero-container">
          {/* Left Column: Heading, Subtitle & Integrated Search Pill */}
          <div className="hero-text-content">
            <h1 className="hero-main-title">
              Track Your <span className="title-highlight">Parcel</span>
            </h1>
            <p className="hero-subtitle-text">
              Enter your tracking number to get real-time updates on your delivery.
            </p>

            {/* Hero Search Bar */}
            <form onSubmit={handleTrackSubmit} className="hero-search-box">
              <Search size={20} className="hero-search-icon" />
              <input
                type="text"
                placeholder="SDP7845621"
                value={trackingInput}
                onChange={(e) => setTrackingInput(e.target.value)}
                className="hero-search-input"
              />
              <button type="submit" className="hero-search-button">
                Track Parcel
              </button>
            </form>

            {searchNotification && (
              <div className="hero-search-toast">
                <CheckCircle2 size={16} />
                <span>{searchNotification}</span>
              </div>
            )}
          </div>

          {/* Right Column: SwiftDrop Delivery Van & City Silhouette */}
          <div className="hero-visual-content">
            <DeliveryVanIllustration />
          </div>
        </div>
      </section>

      {/* 3. Live Tracking Dashboard (Replicating Design Template exactly) */}
      <section id="tracking-dashboard" className="landing-dashboard-section">
        <div className="landing-dashboard-container">
          <div className="tracking-dashboard-grid">
            
            {/* Left Column: Shipment Status & Parcel Details */}
            <div className="dashboard-left-col">
              
              {/* Card 1: Shipment Status */}
              <div className="sd-card shipment-status-card">
                <div className="sd-card-header">
                  <div className="sd-card-icon-square">
                    <Package size={20} color="#F58220" />
                  </div>
                  <h2 className="sd-card-title">Shipment Status</h2>
                </div>

                {/* 5-Step Horizontal Timeline matching template */}
                <div className="sd-stepper-wrapper">
                  <div className="sd-stepper-line">
                    <div className="sd-stepper-progress-fill"></div>
                  </div>

                  <div className="sd-step completed">
                    <div className="sd-step-circle">
                      <CheckCircle2 size={16} />
                    </div>
                    <span className="sd-step-name">Order Created</span>
                    <span className="sd-step-date">Oct 30, 10:14 AM</span>
                  </div>

                  <div className="sd-step completed">
                    <div className="sd-step-circle">
                      <CheckCircle2 size={16} />
                    </div>
                    <span className="sd-step-name">Picked Up</span>
                    <span className="sd-step-date">Oct 30, 02:37 PM</span>
                  </div>

                  <div className="sd-step active">
                    <div className="sd-step-circle active-pulse">
                      <Truck size={15} />
                    </div>
                    <span className="sd-step-name">In Transit</span>
                    <span className="sd-step-date">Oct 31, 09:20 AM</span>
                  </div>

                  <div className="sd-step pending">
                    <div className="sd-step-circle"></div>
                    <span className="sd-step-name">Out for Delivery</span>
                    <span className="sd-step-date">Pending</span>
                  </div>

                  <div className="sd-step pending">
                    <div className="sd-step-circle"></div>
                    <span className="sd-step-name">Delivered</span>
                    <span className="sd-step-date">Pending</span>
                  </div>
                </div>
              </div>

              {/* Card 2: Parcel Details */}
              <div className="sd-card parcel-details-card">
                <div className="sd-card-header">
                  <div className="sd-card-icon-square">
                    <FileText size={20} color="#F58220" />
                  </div>
                  <h2 className="sd-card-title">Parcel Details</h2>
                </div>

                <div className="parcel-details-content-grid">
                  <div className="parcel-info-column">
                    <div className="info-entry">
                      <span className="info-entry-label">Tracking No.</span>
                      <strong className="info-entry-value highlight-navy">{currentTrackingNo}</strong>
                    </div>
                    <div className="info-entry">
                      <span className="info-entry-label">From</span>
                      <span className="info-entry-value">Rajagiriya, Colombo</span>
                    </div>
                    <div className="info-entry">
                      <span className="info-entry-label">To</span>
                      <span className="info-entry-value">Dehiwala</span>
                    </div>
                    <div className="info-entry">
                      <span className="info-entry-label">Service</span>
                      <span className="info-entry-value">Standard (1 – 3 working days)</span>
                    </div>
                  </div>

                  <div className="parcel-estimate-column">
                    <div className="estimate-box">
                      <div className="estimate-header-row">
                        <Calendar size={18} color="#F58220" />
                        <span className="estimate-title">Estimated Delivery</span>
                      </div>
                      <div className="estimate-time-value">Today, 2:30 PM – 4:00 PM</div>
                      <div className="estimate-badge-row">
                        <span className="sd-status-pill in-transit">In Transit</span>
                        <span className="estimate-subtext">On Schedule</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

            </div>

            {/* Right Column: Live Route Map */}
            <div className="dashboard-right-col">
              <div className="sd-card live-map-card">
                <div className="sd-card-header">
                  <div className="sd-card-icon-square">
                    <Navigation size={20} color="#F58220" />
                  </div>
                  <h2 className="sd-card-title">Track in Real Time</h2>
                </div>

                {/* Visual Map Representation matching template */}
                <div className="live-map-surface">
                  {/* Decorative Map Roads Background SVG */}
                  <svg className="map-roads-svg" viewBox="0 0 500 360" fill="none">
                    <rect width="500" height="360" fill="#F8FAFC" />
                    {/* Background secondary roads */}
                    <path d="M-20 60 Q 180 80 520 40" stroke="#E2E8F0" strokeWidth="6" />
                    <path d="M-20 220 Q 240 240 520 180" stroke="#E2E8F0" strokeWidth="8" />
                    <path d="M80 -20 Q 120 180 140 380" stroke="#E2E8F0" strokeWidth="6" />
                    <path d="M340 -20 Q 360 200 420 380" stroke="#E2E8F0" strokeWidth="6" />
                    <path d="M-20 320 L 520 280" stroke="#E2E8F0" strokeWidth="4" />

                    {/* Active Transit Route Line (Colombo to Dehiwala via Kadawatha) */}
                    <path 
                      d="M 90 270 Q 190 140 280 160 T 420 80" 
                      stroke="#CBD5E1" 
                      strokeWidth="5" 
                      strokeDasharray="6 6" 
                    />
                    <path 
                      d="M 90 270 Q 190 140 280 160" 
                      stroke="#F58220" 
                      strokeWidth="5" 
                      strokeLinecap="round" 
                    />
                  </svg>

                  {/* Origin Pin: Colombo */}
                  <div className="map-pin-origin" style={{ left: '18%', bottom: '24%' }}>
                    <div className="pin-dot origin"></div>
                    <span className="pin-label">Rajagiriya</span>
                  </div>

                  {/* Delivery Van Moving Pin */}
                  <div className="map-van-pin" style={{ left: '56%', top: '44%' }}>
                    <div className="van-pulsing-aura"></div>
                    <div className="van-icon-bubble">
                      <Truck size={16} color="#FFFFFF" />
                    </div>
                  </div>

                  {/* Destination Pin: Dehiwala */}
                  <div className="map-pin-destination" style={{ right: '16%', top: '22%' }}>
                    <div className="pin-dot destination">
                      <MapPin size={16} color="#FFFFFF" />
                    </div>
                    <span className="pin-label">Dehiwala</span>
                  </div>

                  {/* Floating Telemetry Info Pill at bottom of map */}
                  <div className="map-telemetry-overlay">
                    <div className="telemetry-van-avatar">
                      <Truck size={18} color="#F58220" />
                    </div>
                    <div className="telemetry-info">
                      <div className="telemetry-code">{currentTrackingNo}</div>
                      <div className="telemetry-eta">ETA: Today, 2:30 PM</div>
                    </div>
                    <span className="sd-status-pill in-transit">In Transit</span>
                  </div>
                </div>
              </div>
            </div>

          </div>
        </div>
      </section>

      {/* 4. Enterprise Services / Features Grid */}
      <section id="services" className="landing-services-section">
        <div className="landing-services-container">
          <div className="section-title-group">
            <span className="section-eyebrow">Enterprise Logistics Network</span>
            <h2 className="section-heading">Built for High-Growth Businesses & Senders</h2>
            <p className="section-description">
              Streamlining parcel distribution with automated sorting, courier mobile workflows, and enterprise API webhooks.
            </p>
          </div>

          <div className="services-card-grid">
            <div className="service-bento-card">
              <div className="service-icon-container">
                <Truck size={24} color="#F58220" />
              </div>
              <h3 className="service-bento-title">Island-Wide 25 Districts</h3>
              <p className="service-bento-text">
                Scheduled daily long-haul connections between Colombo, Kandy, Galle, Jaffna, and Kurunegala regional hubs.
              </p>
            </div>

            <div className="service-bento-card">
              <div className="service-icon-container">
                <CreditCard size={24} color="#F58220" />
              </div>
              <h3 className="service-bento-title">Automated COD Settlements</h3>
              <p className="service-bento-text">
                Fast cash collections deposited directly to your bank account with complete automated reconciliation receipts.
              </p>
            </div>

            <div className="service-bento-card">
              <div className="service-icon-container">
                <Layers size={24} color="#F58220" />
              </div>
              <h3 className="service-bento-title">Developer & Merchant APIs</h3>
              <p className="service-bento-text">
                Ready-to-use REST APIs and real-time webhook endpoints for WooCommerce, Shopify, or custom warehouse systems.
              </p>
            </div>

            <div className="service-bento-card">
              <div className="service-icon-container">
                <ShieldCheck size={24} color="#F58220" />
              </div>
              <h3 className="service-bento-title">Secured RBAC Architecture</h3>
              <p className="service-bento-text">
                Enterprise role-based security separating Customer orders, Merchant stores, Courier tasks, and Admin operations.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 5. Island-Wide Performance Statistics Strip */}
      <section className="landing-stats-strip">
        <div className="landing-stats-container">
          <div className="stat-metric-block">
            <span className="metric-number">25</span>
            <span className="metric-description">Districts Covered Island-Wide</span>
          </div>
          <div className="stat-metric-block">
            <span className="metric-number">99.4%</span>
            <span className="metric-description">On-Time Delivery Success</span>
          </div>
          <div className="stat-metric-block">
            <span className="metric-number">&lt; 12 Hrs</span>
            <span className="metric-description">Colombo Express Dispatch</span>
          </div>
          <div className="stat-metric-block">
            <span className="metric-number">100%</span>
            <span className="metric-description">Insured Transit Protection</span>
          </div>
        </div>
      </section>

      {/* 6. Onboarding CTA Banner */}
      <section className="landing-cta-banner-section">
        <div className="landing-cta-banner-container">
          <div className="cta-banner-card">
            <div className="cta-banner-content">
              <h2 className="cta-banner-title">Ready to streamline your deliveries?</h2>
              <p className="cta-banner-subtitle">
                Join over 2,500 Sri Lankan merchants who trust SwiftDrop for fast, secure, and reliable island-wide fulfillment.
              </p>
            </div>
            <div className="cta-banner-buttons">
              <Link to="/signup" className="btn-cta-primary">
                Create Free Account
                <ArrowRight size={18} />
              </Link>
              <Link to="/login" className="btn-cta-secondary">
                Sign In to Portal
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* 7. Footer (Deep Navy #081F38) */}
      <footer id="about" className="landing-footer-root">
        <div className="landing-footer-container">
          <div className="footer-brand-column">
            <Logo size="medium" dark={true} />
            <p className="footer-brand-tagline">
              Sri Lanka's leading enterprise courier and intelligent parcel distribution network.
            </p>
            <div className="footer-project-info">
              <span>IN3101: Enterprise Application Development</span>
              <span>University of Moratuwa • Group 10 (NineFold)</span>
            </div>
          </div>

          <div className="footer-links-group">
            <h4 className="footer-group-title">Services</h4>
            <ul className="footer-nav-list">
              <li><a href="#tracking-dashboard">Public Parcel Tracking</a></li>
              <li><a href="#services">Next-Day Island Delivery</a></li>
              <li><a href="#services">Cash on Delivery (COD)</a></li>
              <li><a href="#services">Merchant REST API</a></li>
            </ul>
          </div>

          <div className="footer-links-group">
            <h4 className="footer-group-title">Portals</h4>
            <ul className="footer-nav-list">
              <li><Link to="/login">Customer Portal</Link></li>
              <li><Link to="/login">Merchant Dashboard</Link></li>
              <li><Link to="/login">Courier Delivery App</Link></li>
              <li><Link to="/login">Admin Operations</Link></li>
            </ul>
          </div>
        </div>

        <div className="footer-copyright-bar">
          <p>© {new Date().getFullYear()} SwiftDrop Logistics. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}
