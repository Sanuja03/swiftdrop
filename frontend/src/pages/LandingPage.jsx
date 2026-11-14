import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import { 
  Search, 
  Truck, 
  MapPin, 
  Package, 
  CheckCircle2,  
  Calendar, 
  ArrowRight, 
  ShieldCheck,  
  CreditCard, 
  Navigation,
  FileText,
  User,
  LogOut
} from 'lucide-react';
import Logo from '../components/common/Logo';
import DeliveryVanIllustration from '../components/common/DeliveryVanIllustration';
import { useAuth } from '../context/AuthContext';
import { ROUTES } from '@/shared/config/routes';
import './LandingPage.css';

export default function LandingPage() {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const [trackingInput, setTrackingInput] = useState('');
  // The dashboard section below is an illustration with sample values, not live data.
  // Real tracking happens on the public tracking page the search form opens.
  const currentTrackingNo = 'SDP0000000';
  const searchNotification = '';

  const handleTrackSubmit = (e) => {
    e.preventDefault();
    const query = trackingInput.trim().toUpperCase();
    if (!query) return;
    navigate(ROUTES.trackParcel(query));
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
            {isAuthenticated ? (
              <div className="landing-nav-user-group">
                <Link to="/profile" className="btn-nav-profile">
                  <User size={15} />
                  <span>{user?.fullName ? user.fullName.split(' ')[0] : 'My Account'}</span>
                </Link>
                <button onClick={logout} className="btn-nav-logout" title="Sign Out">
                  <LogOut size={15} />
                  <span>Sign Out</span>
                </button>
              </div>
            ) : (
              <>
                <Link to="/login" className="btn-nav-login">Login</Link>
                <motion.div whileHover={{ scale: 1.04 }} whileTap={{ scale: 0.96 }}>
                  <Link to="/signup" className="btn-nav-get-started">Get Started</Link>
                </motion.div>
              </>
            )}
          </div>
        </div>
      </header>

      {/* 2. Hero Section (Track Your Parcel) */}
      <section id="hero" className="landing-hero-section">
        <div className="landing-hero-container">
          {/* Left Column: Heading, Subtitle & Integrated Search Pill */}
          <div className="hero-text-content">
            <motion.h1 
              className="hero-main-title"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, ease: "easeOut" }}
            >
              Track Your <span className="title-highlight">Parcel</span>
            </motion.h1>

            <motion.p 
              className="hero-subtitle-text"
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.12, ease: "easeOut" }}
            >
              Enter your tracking number to get real-time updates on your delivery.
            </motion.p>

            {/* Hero Search Bar with Motion */}
            <motion.form 
              onSubmit={handleTrackSubmit} 
              className="hero-search-box"
              initial={{ opacity: 0, y: 16, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              transition={{ duration: 0.5, delay: 0.22, ease: "easeOut" }}
            >
              <Search size={20} className="hero-search-icon" />
              <input
                type="text"
                placeholder="Enter tracking number"
                value={trackingInput}
                onChange={(e) => setTrackingInput(e.target.value)}
                className="hero-search-input"
              />
              <motion.button 
                type="submit" 
                className="hero-search-button"
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
              >
                Track Parcel
              </motion.button>
            </motion.form>

            {/* Smooth Notification Banner */}
            <AnimatePresence>
              {searchNotification && (
                <motion.div 
                  className="hero-search-toast"
                  initial={{ opacity: 0, y: -8, scale: 0.96 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -6, scale: 0.96 }}
                  transition={{ duration: 0.25 }}
                >
                  <CheckCircle2 size={16} />
                  <span>{searchNotification}</span>
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* Right Column: SwiftDrop Delivery Van with idle floating motion */}
          <motion.div 
            className="hero-visual-content"
            initial={{ opacity: 0, x: 25 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.8, ease: "easeOut" }}
          >
            <motion.div
              animate={{ y: [0, -8, 0] }}
              transition={{ repeat: Infinity, duration: 4.5, ease: "easeInOut" }}
              style={{ width: '100%', display: 'flex', justifyContent: 'center' }}
            >
              <DeliveryVanIllustration />
            </motion.div>
          </motion.div>
        </div>
      </section>

      {/* 3. Live Tracking Dashboard (Replicating Design Template exactly) */}
      <section id="tracking-dashboard" className="landing-dashboard-section">
        <div className="landing-dashboard-container">
          <div className="tracking-dashboard-grid">
            
            {/* Left Column: Shipment Status & Parcel Details */}
            <div className="dashboard-left-col">
              
              {/* Card 1: Shipment Status */}
              <motion.div 
                className="sd-card shipment-status-card"
                initial={{ opacity: 0, y: 24 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, ease: "easeOut" }}
              >
                <div className="sd-card-header">
                  <div className="sd-card-icon-square">
                    <Package size={20} color="#F58220" />
                  </div>
                  <h2 className="sd-card-title">Shipment Status</h2>
                </div>

                {/* 5-Step Horizontal Timeline matching template */}
                <div className="sd-stepper-wrapper">
                  <div className="sd-stepper-line">
                    <motion.div 
                      className="sd-stepper-progress-fill"
                      initial={{ width: "0%" }}
                      whileInView={{ width: "50%" }}
                      viewport={{ once: true }}
                      transition={{ duration: 1.2, ease: "easeOut", delay: 0.2 }}
                    />
                  </div>

                  <div className="sd-step completed">
                    <motion.div 
                      className="sd-step-circle"
                      initial={{ scale: 0 }}
                      whileInView={{ scale: 1 }}
                      viewport={{ once: true }}
                      transition={{ type: "spring", stiffness: 350, damping: 20, delay: 0.1 }}
                    >
                      <CheckCircle2 size={16} />
                    </motion.div>
                    <span className="sd-step-name">Order Created</span>
                    <span className="sd-step-date">Oct 30, 10:14 AM</span>
                  </div>

                  <div className="sd-step completed">
                    <motion.div 
                      className="sd-step-circle"
                      initial={{ scale: 0 }}
                      whileInView={{ scale: 1 }}
                      viewport={{ once: true }}
                      transition={{ type: "spring", stiffness: 350, damping: 20, delay: 0.25 }}
                    >
                      <CheckCircle2 size={16} />
                    </motion.div>
                    <span className="sd-step-name">Picked Up</span>
                    <span className="sd-step-date">Oct 30, 02:37 PM</span>
                  </div>

                  <div className="sd-step active">
                    <motion.div 
                      className="sd-step-circle active-pulse"
                      initial={{ scale: 0 }}
                      whileInView={{ scale: 1 }}
                      viewport={{ once: true }}
                      transition={{ type: "spring", stiffness: 350, damping: 20, delay: 0.4 }}
                    >
                      <Truck size={15} />
                    </motion.div>
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
              </motion.div>

              {/* Card 2: Parcel Details */}
              <motion.div 
                className="sd-card parcel-details-card"
                initial={{ opacity: 0, y: 24 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, delay: 0.12, ease: "easeOut" }}
              >
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
                    <motion.div 
                      className="estimate-box"
                      whileHover={{ scale: 1.015 }}
                      transition={{ duration: 0.2 }}
                    >
                      <div className="estimate-header-row">
                        <Calendar size={18} color="#F58220" />
                        <span className="estimate-title">Estimated Delivery</span>
                      </div>
                      <div className="estimate-time-value">Today, 2:30 PM – 4:00 PM</div>
                      <div className="estimate-badge-row">
                        <span className="sd-status-pill in-transit">In Transit</span>
                        <span className="estimate-subtext">On Schedule</span>
                      </div>
                    </motion.div>
                  </div>
                </div>
              </motion.div>

            </div>

            {/* Right Column: Live Route Map */}
            <div className="dashboard-right-col">
              <motion.div 
                className="sd-card live-map-card"
                initial={{ opacity: 0, y: 24 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, delay: 0.2, ease: "easeOut" }}
              >
                <div className="sd-card-header">
                  <div className="sd-card-icon-square">
                    <Navigation size={20} color="#F58220" />
                  </div>
                  <h2 className="sd-card-title">Track in Real Time</h2>
                </div>

                {/* Visual Map Representation matching template */}
                <div className="live-map-surface">
                  <svg className="map-roads-svg" viewBox="0 0 500 360" fill="none">
                    <rect width="500" height="360" fill="#F8FAFC" />
                    {/* Background secondary roads */}
                    <path d="M-20 60 Q 180 80 520 40" stroke="#E2E8F0" strokeWidth="6" />
                    <path d="M-20 220 Q 240 240 520 180" stroke="#E2E8F0" strokeWidth="8" />
                    <path d="M80 -20 Q 120 180 140 380" stroke="#E2E8F0" strokeWidth="6" />
                    <path d="M340 -20 Q 360 200 420 380" stroke="#E2E8F0" strokeWidth="6" />
                    <path d="M-20 320 L 520 280" stroke="#E2E8F0" strokeWidth="4" />

                    {/* Active Transit Route Line */}
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

                  {/* Delivery Van Moving Pin with Motion Pulse */}
                  <motion.div 
                    className="map-van-pin" 
                    style={{ left: '56%', top: '44%' }}
                    animate={{ y: [0, -3, 0] }}
                    transition={{ repeat: Infinity, duration: 2, ease: "easeInOut" }}
                  >
                    <div className="van-pulsing-aura"></div>
                    <div className="van-icon-bubble">
                      <Truck size={16} color="#FFFFFF" />
                    </div>
                  </motion.div>

                  {/* Destination Pin: Dehiwala */}
                  <div className="map-pin-destination" style={{ right: '16%', top: '22%' }}>
                    <div className="pin-dot destination">
                      <MapPin size={16} color="#FFFFFF" />
                    </div>
                    <span className="pin-label">Dehiwala</span>
                  </div>

                  {/* Floating Telemetry Info Pill */}
                  <motion.div 
                    className="map-telemetry-overlay"
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.3 }}
                    whileHover={{ scale: 1.015 }}
                  >
                    <div className="telemetry-van-avatar">
                      <Truck size={18} color="#F58220" />
                    </div>
                    <div className="telemetry-info">
                      <div className="telemetry-code">{currentTrackingNo}</div>
                      <div className="telemetry-eta">ETA: Today, 2:30 PM</div>
                    </div>
                    <span className="sd-status-pill in-transit">In Transit</span>
                  </motion.div>
                </div>
              </motion.div>
            </div>

          </div>
        </div>
      </section>

      {/* 4. Customer-Focused Services / Features Grid with Staggered Motion */}
      <section id="services" className="landing-services-section">
        <div className="landing-services-container">
          <motion.div 
            className="section-title-group"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5 }}
          >
            <span className="section-eyebrow">Why Sri Lanka Chooses SwiftDrop</span>
            <h2 className="section-heading">Simple, Reliable & Caring Delivery Services</h2>
            <p className="section-description">
              From sending personal gifts to managing daily customer orders for online shops, we make delivery fast, affordable, and stress-free.
            </p>
          </motion.div>

          <div className="services-card-grid">
            {[
              {
                icon: <Truck size={24} color="#F58220" />,
                title: "Guaranteed Next-Day Delivery",
                text: "Fast, dependable courier service connecting Colombo, Kandy, Galle, Jaffna, and all 25 districts with daily scheduled runs."
              },
              {
                icon: <CreditCard size={24} color="#F58220" />,
                title: "Hassle-Free Cash on Delivery (COD)",
                text: "Safe doorstep cash collection for online sellers with fast next-day bank transfers and complete payout summaries."
              },
              {
                icon: <Package size={24} color="#F58220" />,
                title: "Free Doorstep Parcel Pickup",
                text: "Schedule a parcel pickup right from your home, boutique, or warehouse without ever waiting in line at a courier branch."
              },
              {
                icon: <ShieldCheck size={24} color="#F58220" />,
                title: "Live SMS Alerts & Safe Transit",
                text: "Real-time SMS status notifications sent to you and your recipient, tamper-proof packaging, and photo proof of delivery."
              }
            ].map((service, idx) => (
              <motion.div
                key={service.title}
                className="service-bento-card"
                initial={{ opacity: 0, y: 25 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.45, delay: idx * 0.1 }}
                whileHover={{ y: -6, transition: { duration: 0.2 } }}
              >
                <div className="service-icon-container">
                  {service.icon}
                </div>
                <h3 className="service-bento-title">{service.title}</h3>
                <p className="service-bento-text">{service.text}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* 5. Island-Wide Performance Statistics Strip */}
      <section className="landing-stats-strip">
        <div className="landing-stats-container">
          {[
            { num: "25", label: "Districts Covered Island-Wide" },
            { num: "99.4%", label: "Safe On-Time Deliveries" },
            { num: "< 24 Hrs", label: "Express Doorstep Delivery" },
            { num: "100%", label: "Insured Transit Protection" }
          ].map((stat, idx) => (
            <motion.div 
              key={stat.label} 
              className="stat-metric-block"
              initial={{ opacity: 0, scale: 0.9 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 0.4, delay: idx * 0.08 }}
            >
              <span className="metric-number">{stat.num}</span>
              <span className="metric-description">{stat.label}</span>
            </motion.div>
          ))}
        </div>
      </section>

      {/* 6. Onboarding CTA Banner */}
      <section className="landing-cta-banner-section">
        <div className="landing-cta-banner-container">
          <motion.div 
            className="cta-banner-card"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5 }}
          >
            <div className="cta-banner-content">
              <h2 className="cta-banner-title">Ready to send your first parcel?</h2>
              <p className="cta-banner-subtitle">
                Experience island-wide express delivery with convenient doorstep pickup, live SMS tracking, and transparent rates.
              </p>
            </div>
            <div className="cta-banner-buttons">
              <motion.div whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}>
                <Link to="/signup" className="btn-cta-primary">
                  Get Started Free
                  <ArrowRight size={18} />
                </Link>
              </motion.div>
              <motion.div whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}>
                <Link to="/login" className="btn-cta-secondary">
                  Sign In to Portal
                </Link>
              </motion.div>
            </div>
          </motion.div>
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
              <li><a href="#services">Doorstep Pickup Service</a></li>
              <li><a href="#services">Cash on Delivery (COD)</a></li>
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
