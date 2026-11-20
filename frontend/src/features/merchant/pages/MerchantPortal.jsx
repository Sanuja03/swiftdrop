import { useState, useEffect } from 'react';
import { useAuth } from '@/context/AuthContext';
import '../styles/merchant.css';
import { merchantApi } from '../services/merchantApi';
import MyNotifications from '@/features/notifications/pages/MyNotifications';
import Sidebar from '@/shared/layouts/components/Sidebar';
import Topbar from '@/shared/layouts/components/Topbar';
import { MERCHANT_NAVIGATION } from '@/shared/layouts/components/navigation';
import layoutStyles from '@/shared/layouts/CustomerLayout.module.css';

export default function MerchantPortal() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ROLE_ADMIN';
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('dashboard');
  const [profile, setProfile] = useState(null);
  const [stats, setStats] = useState({
    totalOrders: 0,
    activeShipments: 0,
    deliveredOrders: 0,
    pendingOrders: 0,
    cancelledOrders: 0,
  });
  const [orders, setOrders] = useState([]);
  const [orderFilter, setOrderFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState(null);

  // Active Zones catalog
  const [zones, setZones] = useState([
    { id: '1', name: 'Colombo', code: 'CMB' },
    { id: '2', name: 'Gampaha', code: 'GMP' },
    { id: '3', name: 'Kandy', code: 'KDY' },
    { id: '4', name: 'Galle', code: 'GLE' },
    { id: '5', name: 'Matara', code: 'MTR' },
    { id: '6', name: 'Kurunegala', code: 'KRN' },
    { id: '7', name: 'Jaffna', code: 'JFN' },
    { id: '8', name: 'Negombo', code: 'NEG' },
  ]);

  // Dedicated Instant Fee Calculator State
  const [calcForm, setCalcForm] = useState({
    pickupCity: 'Colombo',
    dropoffCity: 'Kandy',
    serviceType: 'STANDARD',
    weightKg: '2.0',
  });
  const [calcResult, setCalcResult] = useState(null);
  const [calcLoading, setCalcLoading] = useState(false);
  const [calcError, setCalcError] = useState(null);

  // Create Order Form State
  const [orderForm, setOrderForm] = useState({
    senderName: '',
    pickupAddress: '',
    pickupCity: 'Colombo',
    recipientName: '',
    recipientPhone: '',
    dropoffAddress: '',
    dropoffCity: 'Colombo',
    serviceType: 'STANDARD',
    weightKg: '1.0',
  });
  const [priceEstimate, setPriceEstimate] = useState(null);
  const [createdOrderModal, setCreatedOrderModal] = useState(null);
  const [selectedOrderDetails, setSelectedOrderDetails] = useState(null);
  const [cancelModalOrder, setCancelModalOrder] = useState(null);

  // API & Webhook Form State
  const [webhookUrl, setWebhookUrl] = useState('');
  const [webhookSecret, setWebhookSecret] = useState('');
  const [webhookLogs, setWebhookLogs] = useState([]);
  const [selectedLogDetails, setSelectedLogDetails] = useState(null);
  const [testResult, setTestResult] = useState(null);
  const [testLoading, setTestLoading] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);
  const [selectedPlatform, setSelectedPlatform] = useState('shopify');
  const [simulatingOrder, setSimulatingOrder] = useState(false);

  // Registration Form State
  const [regForm, setRegForm] = useState({
    businessName: '',
    registrationNumber: '',
    contactPhone: '',
    businessAddress: '',
  });
  const [adminMerchants, setAdminMerchants] = useState([]);

  const [reloadKey, setReloadKey] = useState(0);
  const reloadData = () => setReloadKey((k) => k + 1);

  // Load zones catalog
  useEffect(() => {
    let ignore = false;
    if (typeof merchantApi.getActiveZones === 'function') {
      merchantApi.getActiveZones().then((res) => {
        if (!ignore && Array.isArray(res) && res.length > 0) {
          setZones(res);
        }
      }).catch(() => { });
    }
    return () => { ignore = true; };
  }, []);

  // Load merchant profile, stats, orders, and webhook logs
  useEffect(() => {
    let ignore = false;
    async function fetchData() {
      try {
        if (isAdmin) {
          const adminList = await merchantApi.listAdminMerchants();
          if (ignore) return;
          setAdminMerchants(adminList || []);
          return;
        }

        const prof = await merchantApi.getProfile().catch((err) => {
          if (err.isNotFound) return null;
          throw err;
        });
        if (ignore) return;
        if (!prof) {
          setProfile(null);
          setActiveTab('registration');
          return;
        }
        setProfile(prof);
        setWebhookUrl(prof.webhookUrl || '');
        setWebhookSecret(prof.webhookSecret || '');

        const s = await merchantApi.getStats(prof.id);
        if (ignore) return;
        setStats(s || {
          totalOrders: 0,
          activeShipments: 0,
          deliveredOrders: 0,
          pendingOrders: 0,
          cancelledOrders: 0,
        });

        const ords = await merchantApi.getOrders(prof.id, orderFilter, searchQuery);
        if (ignore) return;
        setOrders(ords || []);

        const logs = await merchantApi.getWebhookLogs(prof.id);
        if (ignore) return;
        setWebhookLogs(logs || []);
      } catch (err) {
        if (!ignore) {
          console.error(err);
          setMessage({ type: 'error', text: 'Error connecting to merchant services: ' + err.message });
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }

    fetchData();
    return () => {
      ignore = true;
    };
  }, [orderFilter, searchQuery, reloadKey, isAdmin]);

  // Recalculate price estimate for Order Booking Form
  useEffect(() => {
    let ignore = false;
    async function updateFee() {
      if (!orderForm.weightKg || isNaN(parseFloat(orderForm.weightKg))) return;
      try {
        const est = await merchantApi.calculateFee({
          serviceType: orderForm.serviceType,
          weightKg: parseFloat(orderForm.weightKg),
          pickupCity: orderForm.pickupCity,
          dropoffCity: orderForm.dropoffCity,
        });
        if (!ignore) setPriceEstimate(est);
      } catch (err) {
        if (!ignore) setPriceEstimate(null);
      }
    }
    updateFee();
    return () => { ignore = true; };
  }, [orderForm.serviceType, orderForm.weightKg, orderForm.pickupCity, orderForm.dropoffCity]);

  // Run calculation in the Dedicated Fee Calculator
  const handleRunCalculator = async (e) => {
    if (e) e.preventDefault();
    setCalcLoading(true);
    setCalcError(null);
    try {
      const res = await merchantApi.calculateFee({
        serviceType: calcForm.serviceType,
        weightKg: parseFloat(calcForm.weightKg),
        pickupCity: calcForm.pickupCity,
        dropoffCity: calcForm.dropoffCity,
      });
      setCalcResult(res);
    } catch (err) {
      setCalcError(err.message || 'Fee calculation failed');
      setCalcResult(null);
    } finally {
      setCalcLoading(false);
    }
  };

  // Run calculator automatically when calculator parameters change
  useEffect(() => {
    if (activeTab === 'calculator') {
      handleRunCalculator();
    }
  }, [calcForm.serviceType, calcForm.weightKg, calcForm.pickupCity, calcForm.dropoffCity, activeTab]);

  // Pre-fill Order Form with Calculator Quote
  const applyCalculatorToOrder = () => {
    setOrderForm({
      ...orderForm,
      pickupCity: calcForm.pickupCity,
      dropoffCity: calcForm.dropoffCity,
      serviceType: calcForm.serviceType,
      weightKg: calcForm.weightKg,
    });
    setActiveTab('create-order');
    setMessage({ type: 'success', text: 'Price quote applied to parcel booking!' });
  };

  // Handle Order Submit
  const handleCreateOrder = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      const res = await merchantApi.createOrder(profile?.id, {
        ...orderForm,
        weightKg: parseFloat(orderForm.weightKg),
      });
      setCreatedOrderModal(res);
      setMessage({ type: 'success', text: `Delivery order booked! Tracking #: ${res.trackingNumber}` });
      setOrderForm({
        senderName: '',
        pickupAddress: '',
        pickupCity: 'Colombo',
        recipientName: '',
        recipientPhone: '',
        dropoffAddress: '',
        dropoffCity: 'Colombo',
        serviceType: 'STANDARD',
        weightKg: '1.0',
      });
      reloadData();
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setLoading(false);
    }
  };

  // Confirm and Execute Safe Order Cancel
  const executeCancelOrder = async (trackingNumber) => {
    try {
      setLoading(true);
      await merchantApi.cancelOrder(profile?.id, trackingNumber);
      setMessage({
        type: 'success',
        text: `Order ${trackingNumber} has been safely cancelled. Courier assignment revoked and returned to available pool.`,
      });
      setCancelModalOrder(null);
      if (selectedOrderDetails?.trackingNumber === trackingNumber) {
        setSelectedOrderDetails(null);
      }
      reloadData();
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setLoading(false);
    }
  };

  // Handle Webhook Config Save
  const handleSaveWebhook = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      const updated = await merchantApi.updateWebhook(profile?.id, {
        webhookUrl,
        webhookSecret,
      });
      setProfile(updated);
      setMessage({ type: 'success', text: 'Store sync settings saved successfully!' });
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setLoading(false);
    }
  };

  // Handle Webhook Test Ping
  const handleTestWebhook = async () => {
    try {
      setTestLoading(true);
      setTestResult(null);
      const res = await merchantApi.testWebhook(profile?.id, webhookUrl);
      setTestResult(res);
      const logs = await merchantApi.getWebhookLogs(profile?.id);
      setWebhookLogs(logs || []);
    } catch (err) {
      setTestResult({ success: false, message: err.message, statusCode: 0, latencyMs: 0 });
    } finally {
      setTestLoading(false);
    }
  };

  // Handle Regenerate API Key
  const handleRegenerateKey = async () => {
    if (!window.confirm('Regenerating your API key will immediately invalidate your previous key. Are you sure you want to rotate it?')) {
      return;
    }
    try {
      setLoading(true);
      const res = await merchantApi.regenerateApiKey(profile?.id);
      setProfile({ ...profile, apiKey: res.apiKey });
      setMessage({ type: 'success', text: 'New API Key generated successfully! Previous key invalidated.' });
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setLoading(false);
    }
  };

  // Simulate an automated store order placement (Shopify/WooCommerce simulation)
  const handleSimulateStoreOrder = async () => {
    try {
      setSimulatingOrder(true);
      const sampleId = Math.floor(1000 + Math.random() * 9000);
      const sampleOrder = {
        senderName: profile?.businessName || 'Online Store',
        pickupAddress: profile?.businessAddress || '100 Galle Road',
        pickupCity: 'Colombo',
        recipientName: `Customer #${sampleId}`,
        recipientPhone: '077' + Math.floor(1000000 + Math.random() * 9000000),
        dropoffAddress: '42 Main Street',
        dropoffCity: 'Kandy',
        serviceType: 'STANDARD',
        weightKg: 1.5,
      };
      const created = await merchantApi.createOrder(profile?.id, sampleOrder);
      setMessage({
        type: 'success',
        text: `🎉 Simulated Online Store Purchase! Order ${created.trackingNumber} automatically booked into SwiftDrop. Check 'My Orders' to see it in real time!`,
      });
      reloadData();
    } catch (err) {
      setMessage({ type: 'error', text: 'Store simulation failed: ' + err.message });
    } finally {
      setSimulatingOrder(false);
    }
  };

  // Handle Merchant Registration
  const handleRegister = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      const res = await merchantApi.registerMerchant(regForm);
      setMessage({ type: 'success', text: `Registration submitted for '${res.businessName}'. Status: PENDING review.` });
      reloadData();
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setLoading(false);
    }
  };

  // Handle Admin Approval
  const handleAdminApprove = async (mId) => {
    try {
      await merchantApi.approveMerchant(mId, 'Approved in Admin Review');
      setMessage({ type: 'success', text: 'Merchant approved! API credentials issued.' });
      reloadData();
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    }
  };

  // Copy text to clipboard with feedback
  const copyToClipboard = (text, label = 'API Key') => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setMessage({ type: 'success', text: `${label} copied to clipboard!` });
    setTimeout(() => setMessage(null), 3500);
  };

  const navItems = MERCHANT_NAVIGATION.map((item) => ({
    ...item,
    isActive: activeTab === item.tab,
    onClick: () => setActiveTab(item.tab),
  }));

  return (
    <div className={layoutStyles.shell}>
      <Sidebar
        isOpen={isMenuOpen}
        onClose={() => setIsMenuOpen(false)}
        items={navItems}
        homeTo="/merchant"
        label="Merchant"
      />
      <div className={layoutStyles.content}>
        <Topbar
          onOpenMenu={() => setIsMenuOpen(true)}
          isMenuOpen={isMenuOpen}
          onNotificationClick={() => setActiveTab('notifications')}
        />
        <main className={layoutStyles.main}>
          <div className="merchant-main">
            {/* Alert Banner / Toast */}
            {message && (
              <div
                className={`merchant-toast-banner ${message.type === 'error' ? 'toast-error' : 'toast-success'}`}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span>{message.type === 'error' ? '⚠️' : '✅'}</span>
                  <span>{message.text}</span>
                </div>
                <button
                  type="button"
                  onClick={() => setMessage(null)}
                  style={{ background: 'transparent', border: 'none', cursor: 'pointer', fontWeight: 800, color: 'inherit' }}
                >
                  ✕
                </button>
              </div>
            )}
            {/* TAB: NOTIFICATIONS */}
            {activeTab === 'notifications' && <MyNotifications />}

            {/* =========================================================================
            TAB 1: ANALYTICS & PERFORMANCE DASHBOARD
           ========================================================================= */}
            {activeTab === 'dashboard' && (
              <div>
                <div className="section-header">
                  <div>
                    <h1 className="section-title">Merchant Analytics & Performance</h1>
                    <p className="section-subtitle">
                      {profile?.businessName && <span>{profile.businessName}</span>}
                      {profile?.businessName ? ' • ' : ''}
                      Real-time metrics tracking for fulfillment volume, active transit pipelines, and delivery success.
                    </p>
                  </div>
                  <div style={{ display: 'flex', gap: '0.75rem' }}>
                    <button
                      type="button"
                      className="btn btn-outline"
                      onClick={() => setActiveTab('calculator')}
                    >
                      🏷️ Live Fee Calculator
                    </button>
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={() => setActiveTab('create-order')}
                    >
                      ➕ New Parcel Booking
                    </button>
                  </div>
                </div>

                {/* Pending Admin Review Alert */}
                {profile?.registrationStatus === 'PENDING' && (
                  <div
                    style={{
                      backgroundColor: '#FEF3C7',
                      border: '1px solid #F59E0B',
                      borderRadius: '8px',
                      padding: '1rem 1.25rem',
                      marginBottom: '1.5rem',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '1rem',
                      flexWrap: 'wrap',
                    }}
                  >
                    <div>
                      <strong style={{ color: '#92400E', fontSize: '1rem' }}>⏳ Account Under Admin Review</strong>
                      <p style={{ color: '#78350F', fontSize: '0.85rem', margin: '0.25rem 0 0 0' }}>
                        Your merchant registration has been submitted and is currently being reviewed by SwiftDrop administrators. Live parcel bookings and webhook integrations will be unlocked upon approval.
                      </p>
                    </div>
                    <button
                      type="button"
                      className="btn btn-outline"
                      style={{ borderColor: '#B45309', color: '#92400E', fontSize: '0.8rem', backgroundColor: '#FFFFFF' }}
                      onClick={() => setActiveTab('registration')}
                    >
                      View Profile Details →
                    </button>
                  </div>
                )}

                {/* 5 Real-Time KPI Cards */}
                <div className="stats-grid">
                  <div className="stat-card" onClick={() => { setOrderFilter('ALL'); setActiveTab('orders'); }}>
                    <div className="stat-icon total">📦</div>
                    <div className="stat-content">
                      <div className="stat-number">{stats.totalOrders}</div>
                      <div className="stat-label">Total Orders</div>
                      <small style={{ color: 'var(--sd-muted)', fontSize: '0.75rem' }}>All-time processed volume</small>
                    </div>
                  </div>

                  <div className="stat-card" onClick={() => { setOrderFilter('CREATED'); setActiveTab('orders'); }}>
                    <div className="stat-icon pending">🕒</div>
                    <div className="stat-content">
                      <div className="stat-number">{stats.pendingOrders}</div>
                      <div className="stat-label">Pending Pickup</div>
                      <small style={{ color: 'var(--sd-muted)', fontSize: '0.75rem' }}>Booked & waiting for couriers</small>
                    </div>
                  </div>

                  <div className="stat-card" onClick={() => { setOrderFilter('ALL'); setActiveTab('orders'); }}>
                    <div className="stat-icon active">🚚</div>
                    <div className="stat-content">
                      <div className="stat-number">{stats.activeShipments}</div>
                      <div className="stat-label">Active Shipments</div>
                      <small style={{ color: 'var(--sd-muted)', fontSize: '0.75rem' }}>Picked Up + In Transit + Out</small>
                    </div>
                  </div>

                  <div className="stat-card" onClick={() => { setOrderFilter('DELIVERED'); setActiveTab('orders'); }}>
                    <div className="stat-icon delivered">✅</div>
                    <div className="stat-content">
                      <div className="stat-number">{stats.deliveredOrders}</div>
                      <div className="stat-label">Delivered Orders</div>
                      <small style={{ color: 'var(--sd-muted)', fontSize: '0.75rem' }}>Successfully delivered</small>
                    </div>
                  </div>

                  <div className="stat-card" onClick={() => { setOrderFilter('CANCELLED'); setActiveTab('orders'); }}>
                    <div className="stat-icon cancelled">🚫</div>
                    <div className="stat-content">
                      <div className="stat-number">{stats.cancelledOrders}</div>
                      <div className="stat-label">Cancelled Orders</div>
                      <small style={{ color: 'var(--sd-muted)', fontSize: '0.75rem' }}>Cancelled prior to pickup</small>
                    </div>
                  </div>
                </div>

                {/* Quick Actions & Recent Deliveries */}
                <div className="card" style={{ marginTop: '1.5rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
                    <div>
                      <h2 className="card-title" style={{ margin: 0 }}>Recent Parcel Bookings</h2>
                      <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--sd-muted)' }}>
                        Latest shipments booked via the portal and merchant API.
                      </p>
                    </div>
                    <button
                      type="button"
                      className="btn btn-outline"
                      onClick={() => setActiveTab('orders')}
                    >
                      View All Orders →
                    </button>
                  </div>

                  <div className="table-responsive">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Tracking Number</th>
                          <th>Recipient</th>
                          <th>Dropoff City</th>
                          <th>Service Level</th>
                          <th>Fee (LKR)</th>
                          <th>Status</th>
                          <th>Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {orders.length === 0 ? (
                          <tr>
                            <td colSpan="7" style={{ textAlign: 'center', color: 'var(--sd-muted)', padding: '2.5rem' }}>
                              No delivery orders yet. Use "+ New Parcel Booking" or integrate via REST API to start fulfilling.
                            </td>
                          </tr>
                        ) : (
                          orders.slice(0, 6).map((ord) => (
                            <tr key={ord.id}>
                              <td style={{ fontWeight: 700, color: 'var(--sd-navy-deep)' }}>
                                <span style={{ fontFamily: 'monospace' }}>{ord.trackingNumber}</span>
                              </td>
                              <td>
                                <div>{ord.recipientName}</div>
                                <small style={{ color: 'var(--sd-muted)' }}>{ord.recipientPhone}</small>
                              </td>
                              <td>{ord.dropoffCity}</td>
                              <td>
                                <span className="service-tag">{ord.serviceType}</span>
                              </td>
                              <td style={{ fontWeight: 700 }}>LKR {parseFloat(ord.fee).toFixed(2)}</td>
                              <td>
                                <span className={`badge badge-${ord.status.toLowerCase().replace(/_/g, '-')}`}>
                                  {ord.statusLabel || ord.status.replace(/_/g, ' ')}
                                </span>
                              </td>
                              <td>
                                <div style={{ display: 'flex', gap: '0.4rem' }}>
                                  <button
                                    type="button"
                                    className="btn btn-outline"
                                    style={{ padding: '0.25rem 0.6rem', fontSize: '0.8rem' }}
                                    onClick={() => setSelectedOrderDetails(ord)}
                                  >
                                    View
                                  </button>
                                  {ord.cancellable && (
                                    <button
                                      type="button"
                                      className="btn btn-danger"
                                      style={{ padding: '0.25rem 0.6rem', fontSize: '0.8rem' }}
                                      onClick={() => setCancelModalOrder(ord)}
                                    >
                                      Cancel
                                    </button>
                                  )}
                                </div>
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            )}

            {/* =========================================================================
            TAB 2: INSTANT PRICING & DELIVERY FEE CALCULATOR
           ========================================================================= */}
            {activeTab === 'calculator' && (
              <div>
                <div className="section-header">
                  <div>
                    <h1 className="section-title">Instant Pricing & Delivery Fee Calculator</h1>
                    <p className="section-subtitle">
                      Calculate guaranteed shipping quotes before booking. Uses SwiftDrop's active zone matrix with base rates, weight brackets, and inter-zone surcharges.
                    </p>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '2rem' }}>
                  {/* Calculator Form */}
                  <div className="card">
                    <h2 className="card-title">🏷️ Live Fee Estimator</h2>
                    <p style={{ fontSize: '0.85rem', color: 'var(--sd-muted)', marginBottom: '1.25rem' }}>
                      Select the pickup and drop-off cities from SwiftDrop's active coverage zones, package weight, and service level.
                    </p>

                    <form onSubmit={handleRunCalculator}>
                      <div className="form-grid">
                        <div className="form-group">
                          <label className="form-label">Pickup City / Zone *</label>
                          <select
                            className="form-select"
                            value={calcForm.pickupCity}
                            onChange={(e) => setCalcForm({ ...calcForm, pickupCity: e.target.value })}
                          >
                            {zones.map((z) => (
                              <option key={`pickup-${z.id || z.code}`} value={z.name}>
                                {z.name} ({z.code})
                              </option>
                            ))}
                          </select>
                        </div>

                        <div className="form-group">
                          <label className="form-label">Drop-off City / Zone *</label>
                          <select
                            className="form-select"
                            value={calcForm.dropoffCity}
                            onChange={(e) => setCalcForm({ ...calcForm, dropoffCity: e.target.value })}
                          >
                            {zones.map((z) => (
                              <option key={`dropoff-${z.id || z.code}`} value={z.name}>
                                {z.name} ({z.code})
                              </option>
                            ))}
                          </select>
                        </div>
                      </div>

                      <div className="form-group" style={{ marginTop: '1.25rem', marginBottom: '1rem' }}>
                        <label className="form-label">Parcel Weight (kg) *</label>
                        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                          <input
                            type="number"
                            step="0.1"
                            min="0.1"
                            max="30"
                            className="form-input"
                            required
                            value={calcForm.weightKg}
                            onChange={(e) => setCalcForm({ ...calcForm, weightKg: e.target.value })}
                          />
                          <div style={{ display: 'flex', gap: '0.25rem' }}>
                            {['1.0', '2.0', '5.0', '10.0'].map((w) => (
                              <button
                                key={w}
                                type="button"
                                className="btn btn-outline"
                                style={{ padding: '0.4rem 0.6rem', fontSize: '0.75rem' }}
                                onClick={() => setCalcForm({ ...calcForm, weightKg: w })}
                              >
                                {w}kg
                              </button>
                            ))}
                          </div>
                        </div>
                      </div>

                      <label className="form-label" style={{ marginTop: '1rem' }}>Service Level *</label>
                      <div className="service-options-grid">
                        <div
                          className={`service-card ${calcForm.serviceType === 'STANDARD' ? 'selected' : ''}`}
                          onClick={() => setCalcForm({ ...calcForm, serviceType: 'STANDARD' })}
                        >
                          <div className="service-card-title">📦 STANDARD</div>
                          <div className="service-card-time">1 - 3 business days</div>
                          <div className="service-card-price">Economical island-wide</div>
                        </div>

                        <div
                          className={`service-card ${calcForm.serviceType === 'EXPRESS' ? 'selected' : ''}`}
                          onClick={() => setCalcForm({ ...calcForm, serviceType: 'EXPRESS' })}
                        >
                          <div className="service-card-title">⚡ EXPRESS</div>
                          <div className="service-card-time">Next business day</div>
                          <div className="service-card-price">Fast prioritized transit</div>
                        </div>

                        <div
                          className={`service-card ${calcForm.serviceType === 'SAME_DAY' ? 'selected' : ''}`}
                          onClick={() => setCalcForm({ ...calcForm, serviceType: 'SAME_DAY' })}
                        >
                          <div className="service-card-title">🚀 SAME_DAY</div>
                          <div className="service-card-time">Within 6 hours</div>
                          <div className="service-card-price">Urgent within active zone</div>
                        </div>
                      </div>

                      <div style={{ marginTop: '1.5rem', display: 'flex', gap: '0.75rem' }}>
                        <button type="submit" className="btn btn-primary" disabled={calcLoading}>
                          {calcLoading ? 'Calculating...' : 'Recalculate Quote'}
                        </button>
                      </div>
                    </form>
                  </div>

                  {/* Live Price Quote Card */}
                  <div>
                    <div className="price-estimate-box">
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                        <h3 style={{ margin: 0, color: 'var(--sd-navy-deep)', fontSize: '1.15rem' }}>
                          📋 Live Fee Breakdown
                        </h3>
                        <span className="badge badge-created">Live Matrix Quote</span>
                      </div>

                      {calcError ? (
                        <div style={{ padding: '1rem', background: '#FEE2E2', color: 'var(--sd-error)', borderRadius: '6px', fontSize: '0.85rem' }}>
                          ⚠️ {calcError}
                        </div>
                      ) : calcResult ? (
                        <div>
                          <div className="price-row">
                            <span>Route:</span>
                            <strong>{calcForm.pickupCity} → {calcForm.dropoffCity}</strong>
                          </div>
                          <div className="price-row">
                            <span>Service Level:</span>
                            <strong>{calcResult.serviceType}</strong>
                          </div>
                          <div className="price-row">
                            <span>Base Fulfillment Fee:</span>
                            <span>LKR {parseFloat(calcResult.baseFee).toFixed(2)}</span>
                          </div>
                          <div className="price-row">
                            <span>Weight Charge ({calcResult.weightKg} kg):</span>
                            <span>LKR {parseFloat(calcResult.weightCharge).toFixed(2)}</span>
                          </div>
                          <div className="price-row">
                            <span>Inter-Zone Surcharge:</span>
                            <span>LKR {parseFloat(calcResult.zoneSurcharge).toFixed(2)}</span>
                          </div>
                          <div className="price-row">
                            <span>Transit Time SLA:</span>
                            <span style={{ fontWeight: 600, color: 'var(--sd-orange)' }}>
                              {calcResult.estimatedDeliveryTime}
                            </span>
                          </div>

                          <div className="price-row total">
                            <span>Guaranteed Total:</span>
                            <span className="price-total-val">
                              LKR {parseFloat(calcResult.totalFee).toFixed(2)}
                            </span>
                          </div>

                          <button
                            type="button"
                            className="btn btn-primary"
                            style={{ width: '100%', marginTop: '1.25rem', padding: '0.8rem', fontSize: '0.95rem' }}
                            onClick={applyCalculatorToOrder}
                          >
                            ➕ Book Parcel with this Quote →
                          </button>
                        </div>
                      ) : (
                        <div style={{ color: 'var(--sd-muted)', fontSize: '0.9rem', textAlign: 'center', padding: '2rem' }}>
                          Select parcel attributes to generate an instant quote.
                        </div>
                      )}
                    </div>

                    {/* Zone Matrix Info Card */}
                    <div className="card" style={{ marginTop: '1.25rem' }}>
                      <h4 style={{ margin: '0 0 0.5rem 0', color: 'var(--sd-navy-deep)' }}>
                        🗺️ SwiftDrop Active Zone Matrix
                      </h4>
                      <p style={{ fontSize: '0.8rem', color: 'var(--sd-muted)', margin: 0, lineHeight: 1.5 }}>
                        Deliveries between distinct zones (e.g. Colombo to Kandy, Galle to Jaffna) automatically apply active inter-zone transit surcharges calculated from our hub routing engine.
                      </p>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem', marginTop: '0.75rem' }}>
                        {zones.map((z) => (
                          <span key={`pill-${z.id || z.code}`} className="badge badge-active">
                            📍 {z.name}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* =========================================================================
            TAB 3: LIVE ORDERS DASHBOARD & SINGLE-CLICK CANCELLATION
           ========================================================================= */}
            {activeTab === 'orders' && (
              <div>
                <div className="section-header">
                  <div>
                    <h1 className="section-title">Order Lifecycle & Live Management</h1>
                    <p className="section-subtitle">
                      Monitor delivery states, inspect recipient drop-offs, and perform safe single-click cancellations.
                    </p>
                  </div>
                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={() => setActiveTab('create-order')}
                  >
                    + Book Delivery
                  </button>
                </div>

                <div className="card">
                  {/* Filter and Search Bar */}
                  <div className="filter-bar">
                    <input
                      type="text"
                      className="search-input"
                      placeholder="🔍 Search by tracking # (e.g. SDP-20261120-XXXXXX), recipient name, phone, or city..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                    />

                    <select
                      className="form-select"
                      value={orderFilter}
                      onChange={(e) => setOrderFilter(e.target.value)}
                      style={{ width: '220px' }}
                    >
                      <option value="ALL">All Statuses ({orders.length})</option>
                      <option value="CREATED">CREATED (Pending Pickup / Cancellable)</option>
                      <option value="PICKED_UP">PICKED_UP (In Courier Custody)</option>
                      <option value="IN_TRANSIT">IN_TRANSIT (Hub Hub Routing)</option>
                      <option value="OUT_FOR_DELIVERY">OUT_FOR_DELIVERY (On Last Mile)</option>
                      <option value="DELIVERED">DELIVERED (Completed)</option>
                      <option value="CANCELLED">CANCELLED (Revoked)</option>
                    </select>

                    <button type="button" className="btn btn-outline" onClick={reloadData}>
                      🔄 Refresh
                    </button>
                  </div>

                  {/* Orders Table */}
                  <div className="table-responsive">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Tracking Number</th>
                          <th>Recipient Details</th>
                          <th>Destination Address</th>
                          <th>Weight</th>
                          <th>Service Level</th>
                          <th>Delivery Fee</th>
                          <th>Status</th>
                          <th>Booked On</th>
                          <th>Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {orders.length === 0 ? (
                          <tr>
                            <td colSpan="9" style={{ textAlign: 'center', padding: '3.5rem', color: 'var(--sd-muted)' }}>
                              No orders matched your filter. Click "+ Book Delivery" or clear the search criteria.
                            </td>
                          </tr>
                        ) : (
                          orders.map((ord) => (
                            <tr key={ord.id}>
                              <td style={{ fontWeight: 700, color: 'var(--sd-navy-deep)' }}>
                                <span style={{ fontFamily: 'monospace', letterSpacing: '0.5px' }}>{ord.trackingNumber}</span>
                              </td>
                              <td>
                                <strong>{ord.recipientName}</strong>
                                <div style={{ fontSize: '0.8rem', color: 'var(--sd-muted)' }}>{ord.recipientPhone}</div>
                              </td>
                              <td>
                                <div>{ord.dropoffAddress}</div>
                                <small style={{ color: 'var(--sd-muted)', fontWeight: 600 }}>{ord.dropoffCity}</small>
                              </td>
                              <td>{ord.weightKg} kg</td>
                              <td>
                                <span className="service-tag">{ord.serviceType}</span>
                              </td>
                              <td style={{ fontWeight: 700, color: 'var(--sd-navy-deep)' }}>
                                LKR {parseFloat(ord.fee).toFixed(2)}
                              </td>
                              <td>
                                <span className={`badge badge-${ord.status.toLowerCase().replace(/_/g, '-')}`}>
                                  {ord.statusLabel || ord.status.replace(/_/g, ' ')}
                                </span>
                              </td>
                              <td style={{ fontSize: '0.8rem', color: 'var(--sd-muted)' }}>
                                {ord.createdAt ? new Date(ord.createdAt).toLocaleDateString() : 'N/A'}
                              </td>
                              <td>
                                <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center' }}>
                                  <button
                                    type="button"
                                    className="btn btn-outline"
                                    style={{ padding: '0.3rem 0.6rem', fontSize: '0.8rem' }}
                                    onClick={() => setSelectedOrderDetails(ord)}
                                  >
                                    Details
                                  </button>
                                  {ord.cancellable ? (
                                    <button
                                      type="button"
                                      className="btn btn-danger"
                                      style={{ padding: '0.3rem 0.6rem', fontSize: '0.8rem' }}
                                      onClick={() => setCancelModalOrder(ord)}
                                      title="Cancel order and revoke courier assignment"
                                    >
                                      Cancel
                                    </button>
                                  ) : ord.status === 'CANCELLED' ? (
                                    <span style={{ fontSize: '0.75rem', color: 'var(--sd-muted)' }}>Revoked</span>
                                  ) : (
                                    <span
                                      style={{ fontSize: '0.75rem', color: 'var(--sd-muted)', cursor: 'not-allowed' }}
                                      title="Cancellation blocked: package has already been picked up by a courier to protect physical inventory."
                                    >
                                      🔒 Locked
                                    </span>
                                  )}
                                </div>
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            )}

            {/* =========================================================================
            TAB 4: PARCEL BOOKING (MANUAL PORTAL BOOKING)
           ========================================================================= */}
            {activeTab === 'create-order' && (
              <div>
                {/* Top Merchant Page Header Banner */}
                <div className="merchant-page-header">
                  <div className="merchant-title-area">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                      <h1 className="merchant-heading">
                        {profile ? profile.businessName : (isAdmin ? 'Admin View' : 'Merchant Portal')}
                      </h1>
                      <span
                        className={`merchant-status-pill ${profile?.registrationStatus === 'APPROVED'
                          ? 'status-approved'
                          : profile?.registrationStatus === 'PENDING'
                            ? 'status-pending'
                            : 'status-rejected'
                          }`}
                      >
                        {profile?.registrationStatus === 'APPROVED' ? '● Approved Merchant' : profile?.registrationStatus || (isAdmin ? 'ADMIN' : 'PENDING')}
                      </span>
                    </div>
                    <p className="merchant-subheading">
                      E-Commerce Logistics & Fulfillment Hub • Live Rates, Orders & Store Integration
                    </p>
                  </div>
                  <div className="merchant-quick-actions">
                    <button
                      type="button"
                      className="btn btn-outline"
                      onClick={() => setActiveTab('calculator')}
                    >
                      🏷️ Fee Calculator
                    </button>
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={() => setActiveTab('create-order')}
                    >
                      ➕ Book Delivery
                    </button>
                  </div>
                </div>

                <div className="section-header">
                  <div>
                    <h1 className="section-title">Manual Parcel Booking</h1>
                    <p className="section-subtitle">
                      Book single parcels directly by entering sender pickup, recipient drop-off, and parcel weight.
                    </p>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '2rem' }}>
                  {/* Booking Form */}
                  <div className="card">
                    <form onSubmit={handleCreateOrder}>
                      <h2 className="card-title">1. Pickup & Sender Details</h2>
                      <div className="form-grid">
                        <div className="form-group">
                          <label className="form-label">Custom Sender / Brand Name (Optional)</label>
                          <input
                            type="text"
                            className="form-input"
                            placeholder={profile?.businessName || 'Your Business Name'}
                            value={orderForm.senderName}
                            onChange={(e) => setOrderForm({ ...orderForm, senderName: e.target.value })}
                          />
                        </div>

                        <div className="form-group">
                          <label className="form-label">Pickup City / Zone *</label>
                          <select
                            className="form-select"
                            required
                            value={orderForm.pickupCity}
                            onChange={(e) => setOrderForm({ ...orderForm, pickupCity: e.target.value })}
                          >
                            {zones.map((z) => (
                              <option key={`book-pickup-${z.id || z.code}`} value={z.name}>
                                {z.name} ({z.code})
                              </option>
                            ))}
                          </select>
                        </div>

                        <div className="form-group" style={{ gridColumn: 'span 2' }}>
                          <label className="form-label">Pickup Street Address *</label>
                          <input
                            type="text"
                            className="form-input"
                            placeholder="Warehouse or store physical street address"
                            required
                            value={orderForm.pickupAddress}
                            onChange={(e) => setOrderForm({ ...orderForm, pickupAddress: e.target.value })}
                          />
                        </div>
                      </div>

                      <h2 className="card-title" style={{ marginTop: '1.75rem' }}>2. Recipient Information</h2>
                      <div className="form-grid">
                        <div className="form-group">
                          <label className="form-label">Recipient Full Name *</label>
                          <input
                            type="text"
                            className="form-input"
                            placeholder="Customer full name"
                            required
                            value={orderForm.recipientName}
                            onChange={(e) => setOrderForm({ ...orderForm, recipientName: e.target.value })}
                          />
                        </div>
                        <div className="form-group">
                          <label className="form-label">Recipient Contact Phone *</label>
                          <input
                            type="tel"
                            className="form-input"
                            placeholder="e.g. 0771234567"
                            required
                            value={orderForm.recipientPhone}
                            onChange={(e) => setOrderForm({ ...orderForm, recipientPhone: e.target.value })}
                          />
                        </div>

                        <div className="form-group">
                          <label className="form-label">Drop-off City / Zone *</label>
                          <select
                            className="form-select"
                            required
                            value={orderForm.dropoffCity}
                            onChange={(e) => setOrderForm({ ...orderForm, dropoffCity: e.target.value })}
                          >
                            {zones.map((z) => (
                              <option key={`book-dropoff-${z.id || z.code}`} value={z.name}>
                                {z.name} ({z.code})
                              </option>
                            ))}
                          </select>
                        </div>

                        <div className="form-group">
                          <label className="form-label">Drop-off Street Address *</label>
                          <input
                            type="text"
                            className="form-input"
                            placeholder="Delivery building, house number, street"
                            required
                            value={orderForm.dropoffAddress}
                            onChange={(e) => setOrderForm({ ...orderForm, dropoffAddress: e.target.value })}
                          />
                        </div>
                      </div>

                      <h2 className="card-title" style={{ marginTop: '1.75rem' }}>3. Package Weight & Service Level</h2>
                      <div className="form-group" style={{ marginBottom: '1rem' }}>
                        <label className="form-label">Weight (kg) *</label>
                        <input
                          type="number"
                          step="0.1"
                          min="0.1"
                          max="30"
                          className="form-input"
                          placeholder="1.0"
                          required
                          value={orderForm.weightKg}
                          onChange={(e) => setOrderForm({ ...orderForm, weightKg: e.target.value })}
                        />
                      </div>

                      <label className="form-label">Select Delivery Option *</label>
                      <div className="service-options-grid">
                        <div
                          className={`service-card ${orderForm.serviceType === 'STANDARD' ? 'selected' : ''}`}
                          onClick={() => setOrderForm({ ...orderForm, serviceType: 'STANDARD' })}
                        >
                          <div className="service-card-title">📦 STANDARD</div>
                          <div className="service-card-time">1 - 3 business days</div>
                          <div className="service-card-price">Regular fulfillment</div>
                        </div>

                        <div
                          className={`service-card ${orderForm.serviceType === 'EXPRESS' ? 'selected' : ''}`}
                          onClick={() => setOrderForm({ ...orderForm, serviceType: 'EXPRESS' })}
                        >
                          <div className="service-card-title">⚡ EXPRESS</div>
                          <div className="service-card-time">Next business day</div>
                          <div className="service-card-price">Fast priority dispatch</div>
                        </div>

                        <div
                          className={`service-card ${orderForm.serviceType === 'SAME_DAY' ? 'selected' : ''}`}
                          onClick={() => setOrderForm({ ...orderForm, serviceType: 'SAME_DAY' })}
                        >
                          <div className="service-card-title">🚀 SAME_DAY</div>
                          <div className="service-card-time">Within 6 hours</div>
                          <div className="service-card-price">Direct express courier</div>
                        </div>
                      </div>

                      <button
                        type="submit"
                        className="btn btn-primary"
                        style={{ width: '100%', marginTop: '1.75rem', padding: '0.9rem', fontSize: '1rem' }}
                        disabled={loading || profile?.registrationStatus !== 'APPROVED'}
                      >
                        {profile?.registrationStatus !== 'APPROVED'
                          ? '⚠️ Merchant Account Pending Approval'
                          : 'Confirm & Generate Parcel Booking'}
                      </button>
                    </form>
                  </div>

                  {/* Price Preview & API Tip */}
                  <div>
                    <div className="price-estimate-box">
                      <h3 style={{ margin: '0 0 1rem 0', color: 'var(--sd-navy-deep)', fontSize: '1.15rem' }}>
                        🏷️ Real-Time Price Estimate
                      </h3>
                      {priceEstimate ? (
                        <div>
                          <div className="price-row">
                            <span>Delivery Service:</span>
                            <strong>{priceEstimate.serviceType}</strong>
                          </div>
                          <div className="price-row">
                            <span>Route:</span>
                            <strong>{orderForm.pickupCity} → {orderForm.dropoffCity}</strong>
                          </div>
                          <div className="price-row">
                            <span>Base Rate:</span>
                            <span>LKR {parseFloat(priceEstimate.baseFee).toFixed(2)}</span>
                          </div>
                          <div className="price-row">
                            <span>Weight Charge ({priceEstimate.weightKg} kg):</span>
                            <span>LKR {parseFloat(priceEstimate.weightCharge).toFixed(2)}</span>
                          </div>
                          <div className="price-row">
                            <span>Inter-Zone Surcharge:</span>
                            <span>LKR {parseFloat(priceEstimate.zoneSurcharge).toFixed(2)}</span>
                          </div>
                          <div className="price-row">
                            <span>Transit Time:</span>
                            <span>{priceEstimate.estimatedDeliveryTime}</span>
                          </div>
                          <div className="price-row total">
                            <span>Total Delivery Fee:</span>
                            <span className="price-total-val">
                              LKR {parseFloat(priceEstimate.totalFee).toFixed(2)}
                            </span>
                          </div>
                        </div>
                      ) : (
                        <div style={{ color: 'var(--sd-muted)', fontSize: '0.85rem' }}>
                          Enter valid weight and pickup/drop-off destinations to view live rates.
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* =========================================================================
            TAB: STORE INTEGRATION & REAL-TIME ORDER STATUS SYNC
           ========================================================================= */}
            {activeTab === 'webhooks' && (
              <div>
                <div className="section-header">
                  <div>
                    <h1 className="section-title">E-Commerce Store Integration & Automation</h1>
                    <p className="section-subtitle">
                      Connect your online store (Shopify, WooCommerce, or custom website) for automated order booking and live customer delivery updates.
                    </p>
                  </div>
                  <div>
                    <span
                      style={{
                        padding: '0.4rem 0.9rem',
                        borderRadius: '20px',
                        fontSize: '0.85rem',
                        fontWeight: 700,
                        backgroundColor: webhookUrl ? '#DCFCE7' : '#F1F5F9',
                        color: webhookUrl ? '#166534' : '#475569',
                        border: `1px solid ${webhookUrl ? '#86EFAC' : '#CBD5E1'}`,
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '0.4rem',
                      }}
                    >
                      <span style={{ fontSize: '0.75rem' }}>{webhookUrl ? '🟢' : '⚪'}</span>
                      {webhookUrl ? 'Live Auto-Sync: CONNECTED' : 'Manual Portal Mode (Not Connected)'}
                    </span>
                  </div>
                </div>

                {/* Practical 3-Step E-Commerce Workflow Banner */}
                <div
                  className="card"
                  style={{
                    marginBottom: '1.5rem',
                    background: 'linear-gradient(135deg, #0B1F38 0%, #102A4C 100%)',
                    color: '#FFFFFF',
                    border: 'none',
                  }}
                >
                  <h3 style={{ margin: '0 0 0.5rem 0', color: '#F58220', fontSize: '1.05rem' }}>
                    ⚡ How Automated Store Integration Works in Daily Practice
                  </h3>
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                      gap: '1rem',
                      marginTop: '1rem',
                    }}
                  >
                    <div style={{ background: 'rgba(255,255,255,0.08)', padding: '0.9rem', borderRadius: '8px' }}>
                      <div style={{ fontSize: '1.25rem', marginBottom: '0.25rem' }}>🛍️ 1. Customer Purchases</div>
                      <p style={{ fontSize: '0.82rem', margin: 0, color: '#E2E8F0', lineHeight: 1.4 }}>
                        A customer checks out on your Shopify, WooCommerce, or custom website store.
                      </p>
                    </div>
                    <div style={{ background: 'rgba(255,255,255,0.08)', padding: '0.9rem', borderRadius: '8px' }}>
                      <div style={{ fontSize: '1.25rem', marginBottom: '0.25rem' }}>⚡ 2. Instant SwiftDrop Booking</div>
                      <p style={{ fontSize: '0.82rem', margin: 0, color: '#E2E8F0', lineHeight: 1.4 }}>
                        Your store uses your <strong>Store API Key</strong> to automatically book the delivery without manual data entry.
                      </p>
                    </div>
                    <div style={{ background: 'rgba(255,255,255,0.08)', padding: '0.9rem', borderRadius: '8px' }}>
                      <div style={{ fontSize: '1.25rem', marginBottom: '0.25rem' }}>🚚 3. Real-Time Status Sync</div>
                      <p style={{ fontSize: '0.82rem', margin: 0, color: '#E2E8F0', lineHeight: 1.4 }}>
                        As couriers pick up and deliver, SwiftDrop pings your store callback URL to update customer order status automatically.
                      </p>
                    </div>
                  </div>
                </div>

                {/* Platform Connection Selector */}
                <div className="card" style={{ marginBottom: '1.5rem' }}>
                  <h3 style={{ margin: '0 0 0.35rem 0', color: 'var(--sd-navy-deep)', fontSize: '1.05rem' }}>
                    🛍️ Select Your Store Platform
                  </h3>
                  <p style={{ color: 'var(--sd-muted)', fontSize: '0.85rem', margin: '0 0 1rem 0' }}>
                    Choose how your store connects to SwiftDrop for streamlined setup instructions.
                  </p>

                  <div className="platform-selector-grid">
                    <div
                      className={`platform-card ${selectedPlatform === 'shopify' ? 'selected' : ''}`}
                      onClick={() => setSelectedPlatform('shopify')}
                    >
                      <div className="platform-card-header">
                        <span className="platform-title">🛍️ Shopify Store</span>
                        <span className="platform-pill">{selectedPlatform === 'shopify' ? 'Active Guide' : 'Select'}</span>
                      </div>
                      <p className="platform-desc">
                        Direct connection with the SwiftDrop Shopify Fulfillment App.
                      </p>
                    </div>

                    <div
                      className={`platform-card ${selectedPlatform === 'woocommerce' ? 'selected' : ''}`}
                      onClick={() => setSelectedPlatform('woocommerce')}
                    >
                      <div className="platform-card-header">
                        <span className="platform-title">🟣 WooCommerce</span>
                        <span className="platform-pill">{selectedPlatform === 'woocommerce' ? 'Active Guide' : 'Select'}</span>
                      </div>
                      <p className="platform-desc">
                        Connect WordPress & WooCommerce via the SwiftDrop shipping plugin.
                      </p>
                    </div>

                    <div
                      className={`platform-card ${selectedPlatform === 'custom' ? 'selected' : ''}`}
                      onClick={() => setSelectedPlatform('custom')}
                    >
                      <div className="platform-card-header">
                        <span className="platform-title">🌐 Custom Website / API</span>
                        <span className="platform-pill">{selectedPlatform === 'custom' ? 'Active Guide' : 'Select'}</span>
                      </div>
                      <p className="platform-desc">
                        For custom React, Next.js, PHP, or enterprise ERP integrations.
                      </p>
                    </div>
                  </div>

                  {/* Platform-Specific Step Guide Box */}
                  <div style={{ background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: '8px', padding: '1rem' }}>
                    {selectedPlatform === 'shopify' && (
                      <div>
                        <strong style={{ color: 'var(--sd-navy-deep)', display: 'block', marginBottom: '0.4rem' }}>
                          🛍️ 3 Easy Steps to Connect Your Shopify Store:
                        </strong>
                        <ol style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.85rem', color: '#334155', lineHeight: 1.6 }}>
                          <li>Copy your <strong>Store API Key</strong> from Step 1 below.</li>
                          <li>In your <strong>Shopify Admin &gt; Settings &gt; Shipping</strong>, paste the API key into the SwiftDrop App.</li>
                          <li>Your store is live! Whenever a customer purchases, SwiftDrop automatically schedules pickup and sends tracking links to customer emails.</li>
                        </ol>
                      </div>
                    )}
                    {selectedPlatform === 'woocommerce' && (
                      <div>
                        <strong style={{ color: 'var(--sd-navy-deep)', display: 'block', marginBottom: '0.4rem' }}>
                          🟣 3 Easy Steps to Connect WooCommerce (WordPress):
                        </strong>
                        <ol style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.85rem', color: '#334155', lineHeight: 1.6 }}>
                          <li>Install the <strong>SwiftDrop Shipping for WooCommerce</strong> plugin on your WordPress site.</li>
                          <li>Paste your <strong>Store API Key</strong> into the plugin settings.</li>
                          <li>Set your <strong>Store Notification URL</strong> below (or click the WooCommerce Preset button) to receive real-time delivery status updates.</li>
                        </ol>
                      </div>
                    )}
                    {selectedPlatform === 'custom' && (
                      <div>
                        <strong style={{ color: 'var(--sd-navy-deep)', display: 'block', marginBottom: '0.4rem' }}>
                          🌐 Custom Website / REST API Integration:
                        </strong>
                        <ol style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.85rem', color: '#334155', lineHeight: 1.6 }}>
                          <li>Send automated order creation requests via <code>POST /api/v1/external/merchant/orders</code> with header <code>X-API-KEY: your_store_key</code>.</li>
                          <li>Register your secure HTTPS callback endpoint in Step 2 below to receive live courier status events.</li>
                          <li>Verify incoming events using the <strong>Security Verification Key</strong> to guarantee authentic delivery updates.</li>
                        </ol>
                      </div>
                    )}
                  </div>
                </div>

                {/* API Credentials for Automated/Programmatic Orders */}
                <div className="card" style={{ marginBottom: '1.5rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <h2 className="card-title" style={{ margin: 0 }}>🔑 Step 1: Your Store's SwiftDrop API Key</h2>
                      <p style={{ color: 'var(--sd-muted)', fontSize: '0.85rem', margin: '0.35rem 0 0 0' }}>
                        Paste this secret key into your website shipping plugin (Shopify / WooCommerce / custom ERP) to authorize automated order creation.
                      </p>
                    </div>
                    <span className="badge badge-active">Live API Key</span>
                  </div>

                  <div className="key-box" style={{ marginTop: '1rem' }}>
                    <span className="key-text" style={{ fontFamily: 'monospace', letterSpacing: '1px' }}>
                      {showApiKey ? (profile?.apiKey || 'No key generated yet') : '••••••••••••••••••••••••••••••••••••••••••••••••'}
                    </span>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button
                        type="button"
                        className="btn btn-outline"
                        style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}
                        onClick={() => setShowApiKey(!showApiKey)}
                      >
                        {showApiKey ? 'Hide' : 'Reveal'}
                      </button>
                      <button
                        type="button"
                        className="btn btn-outline"
                        style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}
                        onClick={() => copyToClipboard(profile?.apiKey, 'API Key')}
                      >
                        📋 Copy Key
                      </button>
                      <button
                        type="button"
                        className="btn btn-outline"
                        style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem', color: 'var(--sd-error)' }}
                        onClick={handleRegenerateKey}
                      >
                        🔄 Rotate Key
                      </button>
                    </div>
                  </div>
                </div>

                {/* Store Connection Configuration Form */}
                <div className="card" style={{ marginBottom: '1.5rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
                    <div>
                      <h2 className="card-title" style={{ margin: 0 }}>🌐 Step 2: Store Connection Settings</h2>
                      <p style={{ color: 'var(--sd-muted)', fontSize: '0.85rem', margin: '0.35rem 0 0 0' }}>
                        Enter where SwiftDrop should send live parcel status changes (Picked Up, In Transit, Delivered) so your store can notify customers automatically.
                      </p>
                    </div>
                    {/* Quick URL Helpers */}
                    <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
                      <button
                        type="button"
                        className="btn btn-outline"
                        style={{ padding: '0.25rem 0.6rem', fontSize: '0.75rem' }}
                        onClick={() => setWebhookUrl('https://httpbin.org/post')}
                        title="Auto-fill with an echo test endpoint for immediate live connection testing"
                      >
                        🪄 Use Echo Test URL
                      </button>
                      <button
                        type="button"
                        className="btn btn-outline"
                        style={{ padding: '0.25rem 0.6rem', fontSize: '0.75rem' }}
                        onClick={() => setWebhookUrl('https://mystore-online.com/wp-json/swiftdrop/v1/updates')}
                        title="Auto-fill with WooCommerce standard endpoint template"
                      >
                        🪄 WooCommerce Preset
                      </button>
                    </div>
                  </div>

                  <form onSubmit={handleSaveWebhook} style={{ marginTop: '1.25rem' }}>
                    <div className="form-group" style={{ marginBottom: '1rem' }}>
                      <label className="form-label">Store Notification URL *</label>
                      <input
                        type="url"
                        className="form-input"
                        placeholder="https://your-store.com/api/order-updates"
                        required
                        value={webhookUrl}
                        onChange={(e) => setWebhookUrl(e.target.value)}
                      />
                      <small style={{ color: 'var(--sd-muted)', marginTop: '0.35rem', display: 'block' }}>
                        The web address on your online store where order status updates should be delivered.
                      </small>
                    </div>

                    <div className="form-group" style={{ marginBottom: '1.25rem' }}>
                      <label className="form-label">Security Verification Key (HMAC Secret)</label>
                      <div style={{ display: 'flex', gap: '0.5rem' }}>
                        <input
                          type="text"
                          className="form-input"
                          placeholder="whsec_..."
                          value={webhookSecret}
                          onChange={(e) => setWebhookSecret(e.target.value)}
                        />
                        <button
                          type="button"
                          className="btn btn-outline"
                          onClick={() => copyToClipboard(webhookSecret, 'Security Key')}
                        >
                          📋 Copy
                        </button>
                      </div>
                      <small style={{ color: 'var(--sd-muted)', marginTop: '0.35rem', display: 'block' }}>
                        Acts as a digital seal. Used by your store plugin to verify that notifications are genuinely from SwiftDrop.
                      </small>
                    </div>

                    <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
                      <button type="submit" className="btn btn-primary" disabled={loading}>
                        💾 Save Settings
                      </button>
                      <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={handleTestWebhook}
                        disabled={!webhookUrl || testLoading}
                      >
                        {testLoading ? 'Testing...' : '🚀 Test Connection'}
                      </button>
                    </div>
                  </form>

                  {/* Test Connection Result Card */}
                  {testResult && (
                    <div
                      style={{
                        marginTop: '1.25rem',
                        padding: '1.1rem',
                        borderRadius: '8px',
                        backgroundColor: testResult.success ? '#DCFCE7' : '#FEE2E2',
                        border: `1px solid ${testResult.success ? '#86EFAC' : '#FCA5A5'}`,
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <strong>{testResult.success ? '✅ Connection Test Successful' : '❌ Connection Test Failed'}</strong>
                        <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>
                          Response Time: {testResult.latencyMs} ms
                        </span>
                      </div>
                      <div style={{ fontSize: '0.85rem', marginTop: '0.35rem' }}>
                        {testResult.message}
                      </div>
                      {testResult.responseBody && (
                        <div style={{ marginTop: '0.5rem', background: '#FFFFFF', padding: '0.5rem', borderRadius: '4px', fontSize: '0.75rem', fontFamily: 'monospace' }}>
                          <strong>Response:</strong> {testResult.responseBody}
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Practical Store Order Simulation Card */}
                <div
                  className="card"
                  style={{
                    marginBottom: '1.5rem',
                    border: '1px solid #BFDBFE',
                    background: 'linear-gradient(135deg, #EFF6FF 0%, #DBEAFE 100%)',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
                    <div>
                      <h3 style={{ margin: '0 0 0.35rem 0', color: '#1E40AF', fontSize: '1.05rem' }}>
                        🧪 Step 3: Interactive Store Order Simulation
                      </h3>
                      <p style={{ margin: 0, fontSize: '0.85rem', color: '#1E3A8A', lineHeight: 1.5 }}>
                        Experience how an automated store checkout works in real time. Simulate a shopper placing an order on your website to verify instant order creation in SwiftDrop.
                      </p>
                    </div>
                    <button
                      type="button"
                      className="btn btn-primary"
                      style={{ backgroundColor: '#1D4ED8', borderColor: '#1D4ED8', whiteSpace: 'nowrap' }}
                      onClick={handleSimulateStoreOrder}
                      disabled={simulatingOrder || profile?.registrationStatus !== 'APPROVED'}
                    >
                      {simulatingOrder ? 'Processing Store Order...' : '🛍️ Simulate Online Store Purchase'}
                    </button>
                  </div>
                </div>

                {/* Reliable Delivery Guarantee Note */}
                <div className="card" style={{ marginBottom: '1.5rem' }}>
                  <h3 style={{ margin: '0 0 0.5rem 0', color: 'var(--sd-navy-deep)' }}>
                    🛡️ Reliable Delivery Guarantee
                  </h3>
                  <p style={{ fontSize: '0.85rem', color: 'var(--sd-muted)', lineHeight: 1.6, margin: 0 }}>
                    If your store website is temporarily unavailable, SwiftDrop automatically retries sending updates up to 3 times so you never miss an order milestone.
                  </p>
                </div>

                {/* Store Activity History Table */}
                <div className="card" style={{ marginTop: '1.5rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                    <h2 className="card-title" style={{ margin: 0 }}>📜 Recent Status Update Activity</h2>
                    <button type="button" className="btn btn-outline" style={{ fontSize: '0.8rem' }} onClick={reloadData}>
                      🔄 Refresh Logs
                    </button>
                  </div>

                  <div className="table-responsive">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Tracking Number</th>
                          <th>Delivery Event</th>
                          <th>Store URL</th>
                          <th>Status Code</th>
                          <th>Delivery Status</th>
                          <th>Attempt</th>
                          <th>Dispatched At</th>
                          <th>Details</th>
                        </tr>
                      </thead>
                      <tbody>
                        {webhookLogs.length === 0 ? (
                          <tr>
                            <td colSpan="8" style={{ textAlign: 'center', color: 'var(--sd-muted)', padding: '2.5rem' }}>
                              No status updates sent yet. Book a delivery or click "Test Connection" to test the connection.
                            </td>
                          </tr>
                        ) : (
                          webhookLogs.map((log) => (
                            <tr key={log.id}>
                              <td style={{ fontWeight: 600 }}>{log.trackingNumber}</td>
                              <td><code>{log.eventType}</code></td>
                              <td style={{ maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                {log.endpointUrl}
                              </td>
                              <td>
                                <strong style={{ color: log.responseCode >= 200 && log.responseCode < 300 ? 'var(--sd-success)' : 'var(--sd-error)' }}>
                                  {log.responseCode || 'ERR'}
                                </strong>
                              </td>
                              <td>
                                <span className={`badge badge-${log.deliveryStatus.toLowerCase()}`}>
                                  {log.deliveryStatus}
                                </span>
                              </td>
                              <td>#{log.attemptCount}</td>
                              <td style={{ fontSize: '0.8rem', color: 'var(--sd-muted)' }}>
                                {new Date(log.createdAt).toLocaleTimeString()}
                              </td>
                              <td>
                                <button
                                  type="button"
                                  className="btn btn-outline"
                                  style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                                  onClick={() => setSelectedLogDetails(log)}
                                >
                                  Inspect
                                </button>
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            )}



            {/* =========================================================================
            TAB 7: ACCOUNT REGISTRATION & ADMIN REVIEW WORKFLOW
           ========================================================================= */}
            {activeTab === 'registration' && (
              <div>
                <div className="section-header">
                  <div>
                    <h1 className="section-title">Merchant Business Profile & Status</h1>
                    <p className="section-subtitle">
                      Official registered company details, contact verification, and fulfillment approval status.
                    </p>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: profile ? '1fr 1fr' : '1fr', gap: '2rem' }}>
                  {/* Profile Card / Registration Form */}
                  <div className="card">
                    {profile ? (
                      <div>
                        <h2 className="card-title">🏢 Merchant Profile Details</h2>
                        <div className="form-group" style={{ marginBottom: '1rem' }}>
                          <label className="form-label">Business Name</label>
                          <div style={{ fontWeight: 700, fontSize: '1.1rem', color: 'var(--sd-navy-deep)' }}>
                            {profile.businessName}
                          </div>
                        </div>
                        <div className="form-group" style={{ marginBottom: '1rem' }}>
                          <label className="form-label">Business Registration (BR) Number</label>
                          <div>{profile.registrationNumber || 'Not specified'}</div>
                        </div>
                        <div className="form-group" style={{ marginBottom: '1rem' }}>
                          <label className="form-label">Contact Phone</label>
                          <div>{profile.contactPhone}</div>
                        </div>
                        <div className="form-group" style={{ marginBottom: '1rem' }}>
                          <label className="form-label">Business Physical Address</label>
                          <div>{profile.businessAddress}</div>
                        </div>
                        <div className="form-group">
                          <label className="form-label">Account Review Status</label>
                          <span className={`merchant-status-pill ${profile.registrationStatus === 'APPROVED' ? 'status-approved' : 'status-pending'
                            }`}>
                            {profile.registrationStatus}
                          </span>
                        </div>
                      </div>
                    ) : (
                      <div>
                        <h2 className="card-title">📝 Register New Merchant Profile</h2>
                        <p style={{ fontSize: '0.85rem', color: 'var(--sd-muted)', marginBottom: '1.25rem' }}>
                          Complete your merchant profile registration to request parcel fulfillment access.
                        </p>
                        <form onSubmit={handleRegister}>
                          <div className="form-group" style={{ marginBottom: '1rem' }}>
                            <label className="form-label">Business Name *</label>
                            <input
                              type="text"
                              className="form-input"
                              placeholder="Your company or store name"
                              required
                              value={regForm.businessName}
                              onChange={(e) => setRegForm({ ...regForm, businessName: e.target.value })}
                            />
                          </div>

                          <div className="form-group" style={{ marginBottom: '1rem' }}>
                            <label className="form-label">Business Registration (BR) Number *</label>
                            <input
                              type="text"
                              className="form-input"
                              placeholder="e.g. PV-123456"
                              required
                              maxLength={100}
                              value={regForm.registrationNumber}
                              onChange={(e) => setRegForm({ ...regForm, registrationNumber: e.target.value })}
                            />
                          </div>

                          <div className="form-group" style={{ marginBottom: '1rem' }}>
                            <label className="form-label">Contact Phone *</label>
                            <input
                              type="tel"
                              className="form-input"
                              placeholder="e.g. 0112345678"
                              required
                              value={regForm.contactPhone}
                              onChange={(e) => setRegForm({ ...regForm, contactPhone: e.target.value })}
                            />
                          </div>

                          <div className="form-group" style={{ marginBottom: '1.25rem' }}>
                            <label className="form-label">Business Physical Address *</label>
                            <input
                              type="text"
                              className="form-input"
                              placeholder="Registered commercial address"
                              required
                              value={regForm.businessAddress}
                              onChange={(e) => setRegForm({ ...regForm, businessAddress: e.target.value })}
                            />
                          </div>

                          <button type="submit" className="btn btn-primary" style={{ width: '100%', padding: '0.85rem' }}>
                            Submit Registration Application
                          </button>
                        </form>
                      </div>
                    )}
                  </div>

                  {/* Admin Review Center (Visible to Admin or for evaluation demo) */}
                  {isAdmin && (
                    <div className="card">
                      <h2 className="card-title">🛡️ Admin / Dispatcher Approval Center</h2>
                      <p style={{ fontSize: '0.85rem', color: 'var(--sd-muted)', marginBottom: '1rem' }}>
                        Admin review process: approve new merchant applications to activate live fulfillment and generate API credentials.
                      </p>

                      <div className="table-responsive">
                        <table className="data-table">
                          <thead>
                            <tr>
                              <th>Business</th>
                              <th>Status</th>
                              <th>Action</th>
                            </tr>
                          </thead>
                          <tbody>
                            {adminMerchants.length === 0 ? (
                              <tr>
                                <td colSpan="3" style={{ textAlign: 'center', padding: '2rem', color: 'var(--sd-muted)' }}>
                                  No merchant applications to review.
                                </td>
                              </tr>
                            ) : (
                              adminMerchants.map((m) => (
                                <tr key={m.id}>
                                  <td>
                                    <strong>{m.businessName}</strong>
                                    <div style={{ fontSize: '0.75rem', color: 'var(--sd-muted)' }}>{m.contactPhone}</div>
                                  </td>
                                  <td>
                                    <span className={`badge badge-${m.registrationStatus.toLowerCase()}`}>
                                      {m.registrationStatus}
                                    </span>
                                  </td>
                                  <td>
                                    {m.registrationStatus === 'PENDING' ? (
                                      <button
                                        type="button"
                                        className="btn btn-primary"
                                        style={{ padding: '0.3rem 0.6rem', fontSize: '0.75rem' }}
                                        onClick={() => handleAdminApprove(m.id)}
                                      >
                                        Approve
                                      </button>
                                    ) : (
                                      <span style={{ fontSize: '0.8rem', color: 'var(--sd-muted)' }}>Reviewed</span>
                                    )}
                                  </td>
                                </tr>
                              ))
                            )}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* =========================================================================
            MODAL: ORDER CREATED CONFIRMATION
           ========================================================================= */}
            {createdOrderModal && (
              <div className="modal-overlay">
                <div className="modal-content">
                  <div className="modal-header">
                    <h3 className="modal-title">🎉 Parcel Successfully Booked!</h3>
                    <button
                      type="button"
                      className="modal-close"
                      onClick={() => setCreatedOrderModal(null)}
                    >
                      ✕
                    </button>
                  </div>
                  <div className="modal-body">
                    <p style={{ color: 'var(--sd-muted)', margin: '0 0 1rem 0' }}>
                      Your delivery order has been confirmed in SwiftDrop and queued for courier pickup.
                    </p>

                    <div style={{ background: '#F8FAFC', padding: '1.25rem', borderRadius: '8px', border: '1px solid var(--sd-border)' }}>
                      <div style={{ fontSize: '0.85rem', color: 'var(--sd-muted)' }}>Official Tracking Number:</div>
                      <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--sd-orange)', letterSpacing: '1px', fontFamily: 'monospace' }}>
                        {createdOrderModal.trackingNumber}
                      </div>

                      <div style={{ marginTop: '1rem', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.6rem', fontSize: '0.9rem' }}>
                        <div><strong>Recipient:</strong> {createdOrderModal.recipientName}</div>
                        <div><strong>Service Level:</strong> {createdOrderModal.serviceType}</div>
                        <div><strong>Destination:</strong> {createdOrderModal.dropoffCity}</div>
                        <div><strong>Delivery Fee:</strong> LKR {parseFloat(createdOrderModal.fee).toFixed(2)}</div>
                      </div>
                    </div>
                  </div>
                  <div className="modal-footer">
                    <button
                      type="button"
                      className="btn btn-outline"
                      onClick={() => copyToClipboard(createdOrderModal.trackingNumber, 'Tracking Number')}
                    >
                      📋 Copy Tracking #
                    </button>
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={() => {
                        setCreatedOrderModal(null);
                        setActiveTab('orders');
                      }}
                    >
                      View in My Orders →
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* =========================================================================
            MODAL: SHIPMENT DETAILS VIEW
           ========================================================================= */}
            {selectedOrderDetails && (
              <div className="modal-overlay">
                <div className="modal-content">
                  <div className="modal-header">
                    <h3 className="modal-title">Shipment: {selectedOrderDetails.trackingNumber}</h3>
                    <button
                      type="button"
                      className="modal-close"
                      onClick={() => setSelectedOrderDetails(null)}
                    >
                      ✕
                    </button>
                  </div>
                  <div className="modal-body">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
                      <span className={`badge badge-${selectedOrderDetails.status.toLowerCase().replace(/_/g, '-')}`}>
                        {selectedOrderDetails.statusLabel || selectedOrderDetails.status.replace(/_/g, ' ')}
                      </span>
                      <span style={{ fontSize: '0.85rem', color: 'var(--sd-muted)' }}>
                        Booked on {selectedOrderDetails.createdAt ? new Date(selectedOrderDetails.createdAt).toLocaleString() : 'N/A'}
                      </span>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem', fontSize: '0.9rem' }}>
                      <div style={{ background: '#F8FAFC', padding: '1rem', borderRadius: '6px' }}>
                        <strong>📍 Pickup Location:</strong>
                        <div style={{ marginTop: '0.25rem' }}>{selectedOrderDetails.pickupAddress}</div>
                        <div style={{ color: 'var(--sd-muted)', fontWeight: 600 }}>{selectedOrderDetails.pickupCity}</div>
                      </div>
                      <div style={{ background: '#F8FAFC', padding: '1rem', borderRadius: '6px' }}>
                        <strong>🎯 Drop-off Destination:</strong>
                        <div style={{ marginTop: '0.25rem' }}>{selectedOrderDetails.dropoffAddress}</div>
                        <div style={{ color: 'var(--sd-muted)', fontWeight: 600 }}>{selectedOrderDetails.dropoffCity}</div>
                      </div>
                      <div>
                        <strong>👤 Recipient Details:</strong>
                        <div>{selectedOrderDetails.recipientName}</div>
                        <div style={{ color: 'var(--sd-muted)' }}>{selectedOrderDetails.recipientPhone}</div>
                      </div>
                      <div>
                        <strong>📦 Package & Charges:</strong>
                        <div>{selectedOrderDetails.weightKg} kg ({selectedOrderDetails.serviceType})</div>
                        <div style={{ fontWeight: 700, color: 'var(--sd-navy-deep)' }}>
                          LKR {parseFloat(selectedOrderDetails.fee).toFixed(2)}
                        </div>
                      </div>
                    </div>
                  </div>
                  <div className="modal-footer">
                    {selectedOrderDetails.cancellable && (
                      <button
                        type="button"
                        className="btn btn-danger"
                        onClick={() => {
                          setCancelModalOrder(selectedOrderDetails);
                        }}
                      >
                        Cancel Order
                      </button>
                    )}
                    <button
                      type="button"
                      className="btn btn-outline"
                      onClick={() => setSelectedOrderDetails(null)}
                    >
                      Close
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* =========================================================================
            MODAL: SAFE ORDER CANCELLATION CONFIRMATION
           ========================================================================= */}
            {cancelModalOrder && (
              <div className="modal-overlay">
                <div className="modal-content">
                  <div className="modal-header">
                    <h3 className="modal-title" style={{ color: 'var(--sd-error)' }}>
                      ⚠️ Confirm Order Cancellation
                    </h3>
                    <button
                      type="button"
                      className="modal-close"
                      onClick={() => setCancelModalOrder(null)}
                    >
                      ✕
                    </button>
                  </div>
                  <div className="modal-body">
                    <p>
                      Are you sure you want to cancel parcel <strong>{cancelModalOrder.trackingNumber}</strong>?
                    </p>
                    <div style={{ background: '#FEF2F2', border: '1px solid #FCA5A5', padding: '1rem', borderRadius: '6px', fontSize: '0.85rem', color: '#991B1B' }}>
                      <strong>Courier De-allocation Guarantee:</strong> If a courier was already assigned by dispatch, the assignment will be automatically revoked and the courier will be safely returned to the available pool.
                    </div>
                    <div style={{ marginTop: '0.75rem', fontSize: '0.8rem', color: 'var(--sd-muted)' }}>
                      Note: Cancellation is permanently blocked once a courier has picked up the physical parcel (PICKED_UP).
                    </div>
                  </div>
                  <div className="modal-footer">
                    <button
                      type="button"
                      className="btn btn-outline"
                      onClick={() => setCancelModalOrder(null)}
                    >
                      Back
                    </button>
                    <button
                      type="button"
                      className="btn btn-danger"
                      onClick={() => executeCancelOrder(cancelModalOrder.trackingNumber)}
                    >
                      Confirm Single-Click Cancellation
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* =========================================================================
            MODAL: WEBHOOK LOG INSPECTION
           ========================================================================= */}
            {selectedLogDetails && (
              <div className="modal-overlay">
                <div className="modal-content" style={{ maxWidth: '650px' }}>
                  <div className="modal-header">
                    <h3 className="modal-title">Inspect Webhook Delivery Log</h3>
                    <button
                      type="button"
                      className="modal-close"
                      onClick={() => setSelectedLogDetails(null)}
                    >
                      ✕
                    </button>
                  </div>
                  <div className="modal-body">
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', fontSize: '0.85rem', marginBottom: '1rem' }}>
                      <div><strong>Event:</strong> <code>{selectedLogDetails.eventType}</code></div>
                      <div><strong>Tracking #:</strong> {selectedLogDetails.trackingNumber}</div>
                      <div>
                        <strong>Status:</strong>{' '}
                        <span className={`badge badge-${selectedLogDetails.deliveryStatus.toLowerCase()}`}>
                          {selectedLogDetails.deliveryStatus}
                        </span>
                      </div>
                      <div>
                        <strong>HTTP Response:</strong>{' '}
                        <span style={{ fontWeight: 700, color: selectedLogDetails.responseCode >= 200 && selectedLogDetails.responseCode < 300 ? 'var(--sd-success)' : 'var(--sd-error)' }}>
                          {selectedLogDetails.responseCode || 'ERR'}
                        </span>
                      </div>
                      <div style={{ gridColumn: 'span 2' }}>
                        <strong>Endpoint:</strong> <code>{selectedLogDetails.endpointUrl}</code>
                      </div>
                    </div>

                    <div style={{ marginBottom: '1rem' }}>
                      <label className="form-label">Request Payload Sent (JSON):</label>
                      <pre className="code-snippet" style={{ maxHeight: '180px', overflowY: 'auto' }}>
                        {(() => {
                          try {
                            return JSON.stringify(JSON.parse(selectedLogDetails.payload), null, 2);
                          } catch {
                            return selectedLogDetails.payload;
                          }
                        })()}
                      </pre>
                    </div>

                    {selectedLogDetails.errorMessage && (
                      <div>
                        <label className="form-label">Server Response / Error Details:</label>
                        <div style={{ background: '#F8FAFC', padding: '0.75rem', borderRadius: '4px', fontSize: '0.8rem', border: '1px solid var(--sd-border)' }}>
                          {selectedLogDetails.errorMessage}
                        </div>
                      </div>
                    )}
                  </div>
                  <div className="modal-footer">
                    <button
                      type="button"
                      className="btn btn-outline"
                      onClick={() => setSelectedLogDetails(null)}
                    >
                      Close
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
