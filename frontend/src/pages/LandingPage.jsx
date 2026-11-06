import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { 
  Search, 
  Truck, 
  MapPin, 
  ShieldCheck, 
  Clock, 
  CreditCard, 
  Layers, 
  ArrowRight, 
  CheckCircle2, 
  Sparkles,
  PhoneCall,
  ChevronRight
} from 'lucide-react';
import Logo from '../components/common/Logo';
import heroVan from '../assets/hero.png';
import './LandingPage.css';

export default function LandingPage() {
  const [trackingId, setTrackingId] = useState('');
  const [searchedId, setSearchedId] = useState('SDP7845621');
  const [searchFeedback, setSearchFeedback] = useState(null);

  const handleTrackSubmit = (e) => {
    e.preventDefault();
    const query = trackingId.trim();
    if (!query) return;
    setSearchedId(query.toUpperCase());
    setSearchFeedback(`Displaying live tracking information for ${query.toUpperCase()}`);
  };

  return (
    <div className="landing-container">
      {/* Top Navbar */}
      <nav className="landing-navbar">
        <div className="landing-navbar-inner">
          <Link to="/" style={{ textDecoration: 'none' }}>
            <Logo size="medium" />
          </Link>

          <div className="landing-nav-links">
            <a href="#tracking-section" className="landing-nav-link">Track Parcel</a>
            <a href="#services" className="landing-nav-link">Services</a>
            <a href="#coverage" className="landing-nav-link">Coverage</a>
            <a href="#features" className="landing-nav-link">For Merchants</a>
          </div>

          <div className="landing-nav-actions">
            <Link to="/login" className="landing-btn-login">Sign In</Link>
            <Link to="/signup" className="landing-btn-signup">Get Started</Link>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="landing-hero">
        <div className="landing-hero-inner">
          <div className="landing-hero-content">
            <div className="landing-pill">
              <span className="landing-pill-dot"></span>
              Island-Wide Courier & Logistics
            </div>
            <h1 className="landing-hero-title">
              Swift, Reliable & Intelligent Parcel Delivery
            </h1>
            <p className="landing-hero-description">
              Next-day delivery across all 25 districts in Sri Lanka. Experience live real-time GPS tracking, automated COD settlements, and developer-friendly e-commerce integration.
            </p>

            <div className="landing-hero-cta">
              <a href="#tracking-section" className="landing-cta-primary">
                <Search size={18} />
                Track a Parcel
              </a>
              <Link to="/signup" className="landing-cta-secondary">
                Register as Merchant
                <ArrowRight size={18} />
              </Link>
            </div>

            <div className="landing-hero-trust">
              <div className="trust-item">
                <ShieldCheck size={18} color="#F58220" />
                <span>100% Insured Transit</span>
              </div>
              <div className="trust-item">
                <Clock size={18} color="#F58220" />
                <span>Next-Day Express Dispatch</span>
              </div>
            </div>
          </div>

          <div className="landing-hero-visual">
            <div className="hero-image-wrapper">
              <img src={heroVan} alt="SwiftDrop Delivery Van" className="hero-van-image" />
              <div className="hero-van-glow"></div>
            </div>
          </div>
        </div>
      </section>

      {/* Public Tracking Lookup Section */}
      <section id="tracking-section" className="landing-tracking-section">
        <div className="tracking-section-inner">
          <div className="tracking-box-card">
            <div className="tracking-card-header">
              <div>
                <h2 className="tracking-title">Track Your Shipment</h2>
                <p className="tracking-subtitle">Enter your SwiftDrop tracking number for live status updates</p>
              </div>
              <span className="tracking-badge">Live System</span>
            </div>

            <form onSubmit={handleTrackSubmit} className="tracking-form">
              <div className="tracking-input-wrapper">
                <Search size={20} color="#64748B" className="tracking-search-icon" />
                <input
                  type="text"
                  placeholder="e.g. SDP7845621"
                  value={trackingId}
                  onChange={(e) => setTrackingId(e.target.value)}
                  className="tracking-input"
                />
              </div>
              <button type="submit" className="tracking-submit-btn">
                Track Parcel
              </button>
            </form>

            {searchFeedback && (
              <div className="tracking-feedback">
                <CheckCircle2 size={16} color="#16A34A" />
                <span>{searchFeedback}</span>
              </div>
            )}

            {/* Simulated Live Tracking Preview */}
            <div className="tracking-result-panel">
              <div className="tracking-summary-row">
                <div className="tracking-id-info">
                  <div className="tracking-truck-circle">
                    <Truck size={22} color="#F58220" />
                  </div>
                  <div>
                    <span className="tracking-code-label">Tracking Number</span>
                    <strong className="tracking-code-val">{searchedId}</strong>
                  </div>
                </div>

                <div className="tracking-route-info">
                  <span className="tracking-route-label">Route</span>
                  <span className="tracking-route-val">Colombo Central Hub → Kandy Distribution Hub</span>
                </div>

                <div className="tracking-status-pill">
                  In Transit
                </div>
              </div>

              {/* Progress Steps Timeline */}
              <div className="tracking-timeline">
                <div className="timeline-step completed">
                  <div className="timeline-node">
                    <CheckCircle2 size={16} />
                  </div>
                  <div className="timeline-text">
                    <span className="step-title">Order Placed</span>
                    <span className="step-time">09:15 AM, Today</span>
                  </div>
                </div>

                <div className="timeline-step completed">
                  <div className="timeline-node">
                    <CheckCircle2 size={16} />
                  </div>
                  <div className="timeline-text">
                    <span className="step-title">Sorted at Hub</span>
                    <span className="step-time">11:30 AM, Colombo</span>
                  </div>
                </div>

                <div className="timeline-step current">
                  <div className="timeline-node">
                    <Truck size={16} />
                  </div>
                  <div className="timeline-text">
                    <span className="step-title">In Transit</span>
                    <span className="step-time">On Route (Kadawatha)</span>
                  </div>
                </div>

                <div className="timeline-step pending">
                  <div className="timeline-node">
                    <span>4</span>
                  </div>
                  <div className="timeline-text">
                    <span className="step-title">Out for Delivery</span>
                    <span className="step-time">ETA 3:30 PM</span>
                  </div>
                </div>

                <div className="timeline-step pending">
                  <div className="timeline-node">
                    <span>5</span>
                  </div>
                  <div className="timeline-text">
                    <span className="step-title">Delivered</span>
                    <span className="step-time">Pending Confirmation</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Stats Counter Bar */}
      <section className="landing-stats-bar">
        <div className="landing-stats-inner">
          <div className="stat-card">
            <span className="stat-number">25</span>
            <span className="stat-label">Districts Covered Island-Wide</span>
          </div>
          <div className="stat-card">
            <span className="stat-number">99.4%</span>
            <span className="stat-label">On-Time Delivery Rate</span>
          </div>
          <div className="stat-card">
            <span className="stat-number">12 Hrs</span>
            <span className="stat-label">Colombo Express Dispatch</span>
          </div>
          <div className="stat-card">
            <span className="stat-number">24/7</span>
            <span className="stat-label">Live GPS Parcel Telemetry</span>
          </div>
        </div>
      </section>

      {/* Services / Feature Grid */}
      <section id="services" className="landing-services-section">
        <div className="landing-services-inner">
          <div className="services-section-header">
            <span className="services-eyebrow">Enterprise Logistics Features</span>
            <h2 className="services-section-title">Built for E-Commerce & Individual Senders</h2>
            <p className="services-section-subtitle">
              Everything you need to send, track, and manage your shipments with speed and confidence.
            </p>
          </div>

          <div className="services-grid">
            <div className="service-card">
              <div className="service-icon-box">
                <Truck size={24} color="#F58220" />
              </div>
              <h3 className="service-card-title">Island-Wide Delivery</h3>
              <p className="service-card-desc">
                Daily scheduled dispatch runs covering all Western, Central, Southern, Northern, and Eastern provinces.
              </p>
            </div>

            <div className="service-card">
              <div className="service-icon-box">
                <CreditCard size={24} color="#F58220" />
              </div>
              <h3 className="service-card-title">Automated COD Settlements</h3>
              <p className="service-card-desc">
                Fast cash collections and automated bank remittances with zero reconciliation delays for registered merchants.
              </p>
            </div>

            <div className="service-card">
              <div className="service-icon-box">
                <Layers size={24} color="#F58220" />
              </div>
              <h3 className="service-card-title">Developer & Merchant APIs</h3>
              <p className="service-card-desc">
                Easily integrate parcel creation and webhook event streaming with your Shopify, WooCommerce, or custom ERP.
              </p>
            </div>

            <div className="service-card">
              <div className="service-icon-box">
                <ShieldCheck size={24} color="#F58220" />
              </div>
              <h3 className="service-card-title">Secured RBAC Architecture</h3>
              <p className="service-card-desc">
                Role-based access control protecting customer data, merchant dashboards, and dispatch routing workflows.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="landing-footer">
        <div className="landing-footer-inner">
          <div className="footer-col-main">
            <Logo size="medium" />
            <p className="footer-tagline">
              Sri Lanka's modern parcel delivery and courier management platform.
            </p>
            <p className="footer-course-note">
              IN3101: Enterprise Application Development • University of Moratuwa
            </p>
          </div>

          <div className="footer-col">
            <h4 className="footer-heading">Quick Links</h4>
            <ul className="footer-links-list">
              <li><a href="#tracking-section">Track Parcel</a></li>
              <li><Link to="/login">Sign In</Link></li>
              <li><Link to="/signup">Register Account</Link></li>
              <li><a href="#services">Services & Rates</a></li>
            </ul>
          </div>

          <div className="footer-col">
            <h4 className="footer-heading">Portals</h4>
            <ul className="footer-links-list">
              <li><Link to="/login">Customer Portal</Link></li>
              <li><Link to="/login">Merchant Dashboard</Link></li>
              <li><Link to="/login">Courier App</Link></li>
              <li><Link to="/login">Dispatcher Central</Link></li>
            </ul>
          </div>
        </div>

        <div className="footer-bottom-bar">
          <p>© {new Date().getFullYear()} SwiftDrop Logistics. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}
