import { useState, useEffect } from 'react';
import '../styles/merchant.css';
import { merchantApi } from '../services/merchantApi';

export default function MerchantPortal() {
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

  // Create Order Form State
  const [orderForm, setOrderForm] = useState({
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

  // API & Webhook Form State
  const [webhookUrl, setWebhookUrl] = useState('');
  const [webhookSecret, setWebhookSecret] = useState('');
  const [webhookLogs, setWebhookLogs] = useState([]);
  const [testResult, setTestResult] = useState(null);
  const [showApiKey, setShowApiKey] = useState(false);

  // Registration Form State
  const [regForm, setRegForm] = useState({
    businessName: '',
    registrationNumber: '',
    contactPhone: '',
    businessAddress: '',
    email: '',
  });
  const [adminMerchants, setAdminMerchants] = useState([]);

  const [reloadKey, setReloadKey] = useState(0);
  const reloadData = () => setReloadKey((k) => k + 1);

  useEffect(() => {
    let ignore = false;
    async function fetchData() {
      try {
        const prof = await merchantApi.getProfile();
        if (ignore) return;
        setProfile(prof);
        setWebhookUrl(prof.webhookUrl || '');
        setWebhookSecret(prof.webhookSecret || '');

        const s = await merchantApi.getStats(prof.id);
        if (ignore) return;
        setStats(s);

        const ords = await merchantApi.getOrders(prof.id, orderFilter, searchQuery);
        if (ignore) return;
        setOrders(ords);

        const logs = await merchantApi.getWebhookLogs(prof.id);
        if (ignore) return;
        setWebhookLogs(logs);

        const adminList = await merchantApi.listAdminMerchants();
        if (ignore) return;
        setAdminMerchants(adminList);
      } catch (err) {
        if (!ignore) {
          console.error(err);
          setMessage({ type: 'error', text: 'Error connecting to backend services: ' + err.message });
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
  }, [orderFilter, searchQuery, reloadKey]);

  // Recalculate price estimate when weight/service/cities change
  useEffect(() => {
    async function updateFee() {
      if (!orderForm.weightKg || isNaN(parseFloat(orderForm.weightKg))) return;
      try {
        const est = await merchantApi.calculateFee({
          serviceType: orderForm.serviceType,
          weightKg: parseFloat(orderForm.weightKg),
          pickupCity: orderForm.pickupCity,
          dropoffCity: orderForm.dropoffCity,
        });
        setPriceEstimate(est);
      } catch (err) {
        console.error('Fee calculation failed', err);
      }
    }
    updateFee();
  }, [orderForm.serviceType, orderForm.weightKg, orderForm.pickupCity, orderForm.dropoffCity]);

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
      setMessage({ type: 'success', text: `Delivery order booked successfully! Tracking #: ${res.trackingNumber}` });
      // Reset form
      setOrderForm({
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

  // Handle Order Cancel
  const handleCancelOrder = async (trackingNumber) => {
    if (!window.confirm(`Are you sure you want to cancel order ${trackingNumber}? This action is only allowed for CREATED orders.`)) {
      return;
    }
    try {
      setLoading(true);
      await merchantApi.cancelOrder(profile?.id, trackingNumber);
      setMessage({ type: 'success', text: `Order ${trackingNumber} has been cancelled.` });
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
      setMessage({ type: 'success', text: 'Webhook settings saved successfully!' });
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setLoading(false);
    }
  };

  // Handle Webhook Test
  const handleTestWebhook = async () => {
    try {
      setTestResult(null);
      const res = await merchantApi.testWebhook(profile?.id, webhookUrl);
      setTestResult(res);
      const logs = await merchantApi.getWebhookLogs(profile?.id);
      setWebhookLogs(logs);
    } catch (err) {
      setTestResult({ success: false, message: err.message });
    }
  };

  // Handle Regenerate API Key
  const handleRegenerateKey = async () => {
    if (!window.confirm('Regenerating your API key will immediately invalidate your previous key. Are you sure?')) {
      return;
    }
    try {
      setLoading(true);
      const res = await merchantApi.regenerateApiKey(profile?.id);
      setProfile({ ...profile, apiKey: res.apiKey });
      setMessage({ type: 'success', text: 'New API Key generated successfully!' });
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setLoading(false);
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
      setMessage({ type: 'success', text: 'Merchant successfully approved! API credentials issued.' });
      reloadData();
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    }
  };

  // Copy API key to clipboard
  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    setMessage({ type: 'success', text: 'API Key copied to clipboard!' });
    setTimeout(() => setMessage(null), 3000);
  };

  return (
    <div className="merchant-container">
      {/* Header */}
      <header className="merchant-header">
        <div className="merchant-brand">
          <div className="merchant-brand-icon">SD</div>
          <div>
            <div className="merchant-brand-name">SwiftDrop</div>
            <div style={{ fontSize: '0.75rem', opacity: 0.8 }}>Enterprise Courier & Logistics</div>
          </div>
          <span className="merchant-brand-badge">Merchant Portal</span>
        </div>

        <nav className="merchant-nav">
          <button
            type="button"
            className={`merchant-nav-tab ${activeTab === 'dashboard' ? 'active' : ''}`}
            onClick={() => setActiveTab('dashboard')}
          >
            📊 Dashboard
          </button>
          <button
            type="button"
            className={`merchant-nav-tab ${activeTab === 'orders' ? 'active' : ''}`}
            onClick={() => setActiveTab('orders')}
          >
            📦 My Orders
          </button>
          <button
            type="button"
            className={`merchant-nav-tab ${activeTab === 'create-order' ? 'active' : ''}`}
            onClick={() => setActiveTab('create-order')}
          >
            ➕ Book Delivery
          </button>
          <button
            type="button"
            className={`merchant-nav-tab ${activeTab === 'api-webhook' ? 'active' : ''}`}
            onClick={() => setActiveTab('api-webhook')}
          >
            ⚡ API & Webhooks
          </button>
          <button
            type="button"
            className={`merchant-nav-tab ${activeTab === 'registration' ? 'active' : ''}`}
            onClick={() => setActiveTab('registration')}
          >
            🏢 Account & Admin
          </button>
        </nav>

        <div className="merchant-user-info">
          <div>
            <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>
              {profile ? profile.businessName : 'Loading...'}
            </div>
            <div style={{ fontSize: '0.75rem', color: '#94A3B8' }}>
              Status:{' '}
              <span
                className={`merchant-status-pill ${
                  profile?.registrationStatus === 'APPROVED'
                    ? 'status-approved'
                    : profile?.registrationStatus === 'PENDING'
                    ? 'status-pending'
                    : 'status-rejected'
                }`}
              >
                {profile?.registrationStatus || 'PENDING'}
              </span>
            </div>
          </div>
        </div>
      </header>

      {/* Notification Toast */}
      {message && (
        <div
          style={{
            padding: '0.85rem 2rem',
            backgroundColor: message.type === 'error' ? '#FEE2E2' : '#DCFCE7',
            color: message.type === 'error' ? 'var(--sd-error)' : 'var(--sd-success)',
            fontWeight: 600,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            borderBottom: '1px solid var(--sd-border)',
          }}
        >
          <span>{message.text}</span>
          <button
            type="button"
            onClick={() => setMessage(null)}
            style={{ background: 'transparent', border: 'none', cursor: 'pointer', fontWeight: 800 }}
          >
            ✕
          </button>
        </div>
      )}

      {/* Main Content */}
      <main className="merchant-main">
        {/* TAB 1: DASHBOARD */}
        {activeTab === 'dashboard' && (
          <div>
            <div className="section-header">
              <div>
                <h1 className="section-title">Merchant Operations Overview</h1>
                <p className="section-subtitle">
                  Monitor delivery volume, live courier shipments, and order fulfillment status.
                </p>
              </div>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setActiveTab('create-order')}
              >
                + New Delivery Order
              </button>
            </div>

            {/* Metrics */}
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-icon total">📦</div>
                <div className="stat-content">
                  <div className="stat-number">{stats.totalOrders}</div>
                  <div className="stat-label">Total Orders</div>
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-icon active">🚚</div>
                <div className="stat-content">
                  <div className="stat-number">{stats.activeShipments}</div>
                  <div className="stat-label">Active in Transit</div>
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-icon delivered">✅</div>
                <div className="stat-content">
                  <div className="stat-number">{stats.deliveredOrders}</div>
                  <div className="stat-label">Delivered</div>
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-icon pending">🕒</div>
                <div className="stat-content">
                  <div className="stat-number">{stats.pendingOrders}</div>
                  <div className="stat-label">Pending Pickup</div>
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-icon cancelled">🚫</div>
                <div className="stat-content">
                  <div className="stat-number">{stats.cancelledOrders}</div>
                  <div className="stat-label">Cancelled</div>
                </div>
              </div>
            </div>

            {/* Quick Actions & Recent Orders */}
            <div className="card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <h2 className="card-title" style={{ margin: 0 }}>Recent Deliveries</h2>
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
                      <th>Service</th>
                      <th>Fee (LKR)</th>
                      <th>Status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {orders.length === 0 ? (
                      <tr>
                        <td colSpan="7" style={{ textAlign: 'center', color: 'var(--sd-muted)', padding: '2rem' }}>
                          No delivery orders yet. Click "+ New Delivery Order" to book your first delivery.
                        </td>
                      </tr>
                    ) : (
                      orders.slice(0, 5).map((ord) => (
                        <tr key={ord.id}>
                          <td style={{ fontWeight: 700, color: 'var(--sd-navy-deep)' }}>
                            {ord.trackingNumber}
                          </td>
                          <td>
                            <div>{ord.recipientName}</div>
                            <small style={{ color: 'var(--sd-muted)' }}>{ord.recipientPhone}</small>
                          </td>
                          <td>{ord.dropoffCity}</td>
                          <td>{ord.serviceType}</td>
                          <td style={{ fontWeight: 600 }}>{parseFloat(ord.fee).toFixed(2)}</td>
                          <td>
                            <span className={`badge badge-${ord.status.toLowerCase().replace(/_/g, '-')}`}>
                              {ord.statusLabel || ord.status.replace(/_/g, ' ')}
                            </span>
                          </td>
                          <td>
                            <button
                              type="button"
                              className="btn btn-outline"
                              style={{ padding: '0.25rem 0.5rem', fontSize: '0.8rem' }}
                              onClick={() => setSelectedOrderDetails(ord)}
                            >
                              Details
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

        {/* TAB 2: MY ORDERS */}
        {activeTab === 'orders' && (
          <div>
            <div className="section-header">
              <div>
                <h1 className="section-title">Delivery Orders Management</h1>
                <p className="section-subtitle">
                  Search, filter, track, and manage all your customer delivery requests.
                </p>
              </div>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setActiveTab('create-order')}
              >
                + Create New Order
              </button>
            </div>

            <div className="card">
              {/* Filter & Search */}
              <div className="filter-bar">
                <input
                  type="text"
                  className="search-input"
                  placeholder="🔍 Search by tracking # (e.g. SDP7845621), recipient name, or city..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
                <select
                  className="form-select"
                  value={orderFilter}
                  onChange={(e) => setOrderFilter(e.target.value)}
                  style={{ width: '200px' }}
                >
                  <option value="ALL">All Statuses</option>
                  <option value="CREATED">Created (Cancellable)</option>
                  <option value="PICKED_UP">Picked Up</option>
                  <option value="IN_TRANSIT">In Transit</option>
                  <option value="OUT_FOR_DELIVERY">Out for Delivery</option>
                  <option value="DELIVERED">Delivered</option>
                  <option value="FAILED">Failed</option>
                  <option value="CANCELLED">Cancelled</option>
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
                      <th>Tracking #</th>
                      <th>Recipient Details</th>
                      <th>Dropoff Address</th>
                      <th>Weight</th>
                      <th>Service Level</th>
                      <th>Delivery Fee</th>
                      <th>Status</th>
                      <th>Created</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {orders.length === 0 ? (
                      <tr>
                        <td colSpan="9" style={{ textAlign: 'center', padding: '3rem', color: 'var(--sd-muted)' }}>
                          No orders matched your search criteria.
                        </td>
                      </tr>
                    ) : (
                      orders.map((ord) => (
                        <tr key={ord.id}>
                          <td style={{ fontWeight: 700, color: 'var(--sd-navy-deep)' }}>
                            {ord.trackingNumber}
                          </td>
                          <td>
                            <strong>{ord.recipientName}</strong>
                            <div style={{ fontSize: '0.8rem', color: 'var(--sd-muted)' }}>{ord.recipientPhone}</div>
                          </td>
                          <td>
                            <div>{ord.dropoffAddress}</div>
                            <small style={{ color: 'var(--sd-muted)' }}>{ord.dropoffCity}</small>
                          </td>
                          <td>{ord.weightKg} kg</td>
                          <td>
                            <span style={{ fontWeight: 600 }}>{ord.serviceType}</span>
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
                            <div style={{ display: 'flex', gap: '0.4rem' }}>
                              <button
                                type="button"
                                className="btn btn-outline"
                                style={{ padding: '0.3rem 0.6rem', fontSize: '0.8rem' }}
                                onClick={() => setSelectedOrderDetails(ord)}
                              >
                                View
                              </button>
                              {ord.cancellable && (
                                <button
                                  type="button"
                                  className="btn btn-danger"
                                  style={{ padding: '0.3rem 0.6rem', fontSize: '0.8rem' }}
                                  onClick={() => handleCancelOrder(ord.trackingNumber)}
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

        {/* TAB 3: CREATE ORDER */}
        {activeTab === 'create-order' && (
          <div>
            <div className="section-header">
              <div>
                <h1 className="section-title">Book a Delivery Order</h1>
                <p className="section-subtitle">
                  Create customer delivery orders via the portal or automated REST API.
                </p>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1.5rem' }}>
              {/* Form */}
              <div className="card">
                <form onSubmit={handleCreateOrder}>
                  <h2 className="card-title">1. Pickup & Sender Details</h2>
                  <div className="form-grid">
                    <div className="form-group">
                      <label className="form-label">Pickup Address *</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="e.g. No. 42, Galle Road"
                        required
                        value={orderForm.pickupAddress}
                        onChange={(e) => setOrderForm({ ...orderForm, pickupAddress: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Pickup City *</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="Colombo"
                        required
                        value={orderForm.pickupCity}
                        onChange={(e) => setOrderForm({ ...orderForm, pickupCity: e.target.value })}
                      />
                    </div>
                  </div>

                  <h2 className="card-title" style={{ marginTop: '1.5rem' }}>2. Recipient Details</h2>
                  <div className="form-grid">
                    <div className="form-group">
                      <label className="form-label">Recipient Full Name *</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="Kamal Perera"
                        required
                        value={orderForm.recipientName}
                        onChange={(e) => setOrderForm({ ...orderForm, recipientName: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Recipient Phone Number *</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="0771234567"
                        required
                        value={orderForm.recipientPhone}
                        onChange={(e) => setOrderForm({ ...orderForm, recipientPhone: e.target.value })}
                      />
                    </div>
                  </div>

                  <div className="form-grid">
                    <div className="form-group">
                      <label className="form-label">Dropoff Delivery Address *</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="No. 12, Kandy Road"
                        required
                        value={orderForm.dropoffAddress}
                        onChange={(e) => setOrderForm({ ...orderForm, dropoffAddress: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Dropoff City *</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="Kandy / Dehiwala / Galle"
                        required
                        value={orderForm.dropoffCity}
                        onChange={(e) => setOrderForm({ ...orderForm, dropoffCity: e.target.value })}
                      />
                    </div>
                  </div>

                  <h2 className="card-title" style={{ marginTop: '1.5rem' }}>3. Package Details & Service Level</h2>
                  <div className="form-group" style={{ marginBottom: '1rem' }}>
                    <label className="form-label">Package Weight (kg) *</label>
                    <input
                      type="number"
                      step="0.1"
                      min="0.1"
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
                      <div className="service-card-title">📦 Standard</div>
                      <div className="service-card-time">1 - 3 working days</div>
                      <div className="service-card-price">From LKR 500</div>
                    </div>

                    <div
                      className={`service-card ${orderForm.serviceType === 'EXPRESS' ? 'selected' : ''}`}
                      onClick={() => setOrderForm({ ...orderForm, serviceType: 'EXPRESS' })}
                    >
                      <div className="service-card-title">⚡ Express</div>
                      <div className="service-card-time">Next day delivery</div>
                      <div className="service-card-price">From LKR 900</div>
                    </div>

                    <div
                      className={`service-card ${orderForm.serviceType === 'SAME_DAY' ? 'selected' : ''}`}
                      onClick={() => setOrderForm({ ...orderForm, serviceType: 'SAME_DAY' })}
                    >
                      <div className="service-card-title">🚀 Same-Day</div>
                      <div className="service-card-time">Within 6 hours</div>
                      <div className="service-card-price">From LKR 1,400</div>
                    </div>
                  </div>

                  <button
                    type="submit"
                    className="btn btn-primary"
                    style={{ width: '100%', marginTop: '1.5rem', padding: '0.85rem' }}
                    disabled={loading || profile?.registrationStatus !== 'APPROVED'}
                  >
                    {profile?.registrationStatus !== 'APPROVED'
                      ? '⚠️ Account Pending Approval - Cannot Book'
                      : 'Confirm & Generate Delivery Order'}
                  </button>
                </form>
              </div>

              {/* Price Estimate Card */}
              <div>
                <div className="price-estimate-box">
                  <h3 style={{ margin: '0 0 1rem 0', color: 'var(--sd-navy-deep)', fontSize: '1.1rem' }}>
                    🏷️ Real-Time Price Breakdown
                  </h3>
                  {priceEstimate ? (
                    <div>
                      <div className="price-row">
                        <span>Delivery Service:</span>
                        <strong>{priceEstimate.serviceType}</strong>
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
                        <span>Inter-City Zone Surcharge:</span>
                        <span>LKR {parseFloat(priceEstimate.zoneSurcharge).toFixed(2)}</span>
                      </div>
                      <div className="price-row">
                        <span>Estimated Transit:</span>
                        <span>{priceEstimate.estimatedDeliveryTime}</span>
                      </div>
                      <div className="price-row total">
                        <span>Estimated Total:</span>
                        <span className="price-total-val">
                          LKR {parseFloat(priceEstimate.totalFee).toFixed(2)}
                        </span>
                      </div>
                    </div>
                  ) : (
                    <div style={{ color: 'var(--sd-muted)', fontSize: '0.9rem' }}>
                      Enter weight and destinations to view real-time delivery rate calculation.
                    </div>
                  )}
                </div>

                <div className="card">
                  <h4 style={{ margin: '0 0 0.5rem 0', color: 'var(--sd-navy-deep)' }}>
                    💡 Automated API Available
                  </h4>
                  <p style={{ fontSize: '0.85rem', color: 'var(--sd-muted)', lineHeight: 1.5 }}>
                    You can book orders automatically from your ecommerce website or inventory system using our RESTful API endpoint:
                  </p>
                  <code style={{ fontSize: '0.75rem', background: '#F1F5F9', padding: '0.4rem', borderRadius: '4px', display: 'block', wordBreak: 'break-all' }}>
                    POST /api/v1/merchant/api/v1/orders
                  </code>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: API & WEBHOOKS */}
        {activeTab === 'api-webhook' && (
          <div>
            <div className="section-header">
              <div>
                <h1 className="section-title">API Credentials & Webhook Integration</h1>
                <p className="section-subtitle">
                  Connect external systems, manage secret tokens, and receive live status callbacks.
                </p>
              </div>
            </div>

            {/* API Key Management */}
            <div className="card">
              <h2 className="card-title">🔑 Merchant API Key (REST Integration)</h2>
              <p style={{ color: 'var(--sd-muted)', fontSize: '0.9rem', marginBottom: '1rem' }}>
                Include this key as the <code>X-API-KEY</code> HTTP header when calling external endpoints. Keep your API key strictly confidential.
              </p>

              <div className="key-box">
                <span className="key-text">
                  {showApiKey ? profile?.apiKey : '••••••••••••••••••••••••••••••••••••••••'}
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
                    onClick={() => copyToClipboard(profile?.apiKey)}
                  >
                    📋 Copy
                  </button>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1rem' }}>
                <button
                  type="button"
                  className="btn btn-outline"
                  style={{ color: 'var(--sd-error)' }}
                  onClick={handleRegenerateKey}
                >
                  🔄 Regenerate API Key
                </button>
              </div>
            </div>

            {/* Webhook Configuration */}
            <div className="card">
              <h2 className="card-title">📡 Real-Time Webhook Configuration</h2>
              <p style={{ color: 'var(--sd-muted)', fontSize: '0.9rem', marginBottom: '1rem' }}>
                SwiftDrop pushes automated HTTP POST notifications to this endpoint whenever a shipment status changes (Picked Up, In Transit, Delivered, Failed, Cancelled).
              </p>

              <form onSubmit={handleSaveWebhook}>
                <div className="form-group" style={{ marginBottom: '1rem' }}>
                  <label className="form-label">Webhook URL Endpoint *</label>
                  <input
                    type="url"
                    className="form-input"
                    placeholder="https://yourdomain.com/api/swiftdrop-events"
                    required
                    value={webhookUrl}
                    onChange={(e) => setWebhookUrl(e.target.value)}
                  />
                </div>

                <div className="form-group" style={{ marginBottom: '1.25rem' }}>
                  <label className="form-label">HMAC-SHA256 Signature Secret</label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="whsec_..."
                    value={webhookSecret}
                    onChange={(e) => setWebhookSecret(e.target.value)}
                  />
                  <small style={{ color: 'var(--sd-muted)', marginTop: '0.25rem' }}>
                    Incoming webhooks include a <code>X-SwiftDrop-Signature</code> header signed with this secret for payload verification.
                  </small>
                </div>

                <div style={{ display: 'flex', gap: '0.75rem' }}>
                  <button type="submit" className="btn btn-primary">
                    💾 Save Webhook Settings
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={handleTestWebhook}
                    disabled={!webhookUrl}
                  >
                    🚀 Send Test Ping
                  </button>
                </div>
              </form>

              {/* Test Result Banner */}
              {testResult && (
                <div
                  style={{
                    marginTop: '1.25rem',
                    padding: '1rem',
                    borderRadius: '8px',
                    backgroundColor: testResult.success ? '#DCFCE7' : '#FEE2E2',
                    border: `1px solid ${testResult.success ? '#86EFAC' : '#FCA5A5'}`,
                  }}
                >
                  <strong>{testResult.success ? '✅ Test Webhook Delivered' : '❌ Delivery Failed'}</strong>
                  <div style={{ fontSize: '0.85rem', marginTop: '0.25rem' }}>
                    {testResult.message} ({testResult.latencyMs} ms)
                  </div>
                </div>
              )}
            </div>

            {/* Webhook Delivery Logs */}
            <div className="card">
              <h2 className="card-title">📜 Webhook Event Dispatch History</h2>
              <div className="table-responsive">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Tracking #</th>
                      <th>Event</th>
                      <th>Destination URL</th>
                      <th>HTTP Code</th>
                      <th>Delivery Status</th>
                      <th>Attempt</th>
                      <th>Timestamp</th>
                    </tr>
                  </thead>
                  <tbody>
                    {webhookLogs.length === 0 ? (
                      <tr>
                        <td colSpan="7" style={{ textAlign: 'center', color: 'var(--sd-muted)', padding: '2rem' }}>
                          No webhook events dispatched yet. Click "Send Test Ping" or create a delivery order to trigger events.
                        </td>
                      </tr>
                    ) : (
                      webhookLogs.map((log) => (
                        <tr key={log.id}>
                          <td style={{ fontWeight: 600 }}>{log.trackingNumber}</td>
                          <td><code>{log.eventType}</code></td>
                          <td style={{ maxWidth: '250px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
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
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* TAB 5: REGISTRATION & ADMIN WORKFLOW */}
        {activeTab === 'registration' && (
          <div>
            <div className="section-header">
              <div>
                <h1 className="section-title">Merchant Account & Dispatcher/Admin Review</h1>
                <p className="section-subtitle">
                  Test the complete merchant lifecycle: registration submission, admin approval, and credential issuing.
                </p>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
              {/* Registration Form */}
              <div className="card">
                <h2 className="card-title">📝 Register New Merchant Profile</h2>
                <form onSubmit={handleRegister}>
                  <div className="form-group" style={{ marginBottom: '1rem' }}>
                    <label className="form-label">Business Name *</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. Ceylon Spices & Crafts"
                      required
                      value={regForm.businessName}
                      onChange={(e) => setRegForm({ ...regForm, businessName: e.target.value })}
                    />
                  </div>

                  <div className="form-group" style={{ marginBottom: '1rem' }}>
                    <label className="form-label">Business Registration (BR) Number</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="PV-789012"
                      value={regForm.registrationNumber}
                      onChange={(e) => setRegForm({ ...regForm, registrationNumber: e.target.value })}
                    />
                  </div>

                  <div className="form-group" style={{ marginBottom: '1rem' }}>
                    <label className="form-label">Contact Phone *</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="0112345678"
                      required
                      value={regForm.contactPhone}
                      onChange={(e) => setRegForm({ ...regForm, contactPhone: e.target.value })}
                    />
                  </div>

                  <div className="form-group" style={{ marginBottom: '1rem' }}>
                    <label className="form-label">Business Physical Address *</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="No. 100, Havelock Road, Colombo"
                      required
                      value={regForm.businessAddress}
                      onChange={(e) => setRegForm({ ...regForm, businessAddress: e.target.value })}
                    />
                  </div>

                  <button type="submit" className="btn btn-primary" style={{ width: '100%' }}>
                    Submit Registration Application
                  </button>
                </form>
              </div>

              {/* Admin Review Simulation */}
              <div className="card">
                <h2 className="card-title">🛡️ Admin / Dispatcher Review Center</h2>
                <p style={{ fontSize: '0.85rem', color: 'var(--sd-muted)', marginBottom: '1rem' }}>
                  In the SwiftDrop architecture, registrations are reviewed by an Admin. Approving automatically issues the merchant API credentials.
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
                      {adminMerchants.map((m) => (
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
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Modal: Order Created Confirmation */}
        {createdOrderModal && (
          <div className="modal-overlay">
            <div className="modal-content">
              <div className="modal-header">
                <h3 className="modal-title">🎉 Delivery Order Created!</h3>
                <button
                  type="button"
                  className="modal-close"
                  onClick={() => setCreatedOrderModal(null)}
                >
                  ✕
                </button>
              </div>
              <div className="modal-body">
                <p style={{ color: 'var(--sd-muted)' }}>
                  Your delivery order has been booked in the SwiftDrop system and queued for courier pickup.
                </p>

                <div style={{ background: '#F8FAFC', padding: '1rem', borderRadius: '8px', border: '1px solid var(--sd-border)' }}>
                  <div style={{ fontSize: '0.85rem', color: 'var(--sd-muted)' }}>Tracking Number:</div>
                  <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--sd-orange)', letterSpacing: '1px' }}>
                    {createdOrderModal.trackingNumber}
                  </div>

                  <div style={{ marginTop: '0.75rem', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', fontSize: '0.9rem' }}>
                    <div><strong>Recipient:</strong> {createdOrderModal.recipientName}</div>
                    <div><strong>Service:</strong> {createdOrderModal.serviceType}</div>
                    <div><strong>Dropoff:</strong> {createdOrderModal.dropoffCity}</div>
                    <div><strong>Delivery Fee:</strong> LKR {parseFloat(createdOrderModal.fee).toFixed(2)}</div>
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => {
                    setCreatedOrderModal(null);
                    setActiveTab('orders');
                  }}
                >
                  View in Orders List
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Modal: Order Details View */}
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
                <div style={{ marginBottom: '1rem' }}>
                  <span className={`badge badge-${selectedOrderDetails.status.toLowerCase().replace(/_/g, '-')}`}>
                    {selectedOrderDetails.statusLabel || selectedOrderDetails.status.replace(/_/g, ' ')}
                  </span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', fontSize: '0.9rem' }}>
                  <div>
                    <strong>Pickup From:</strong>
                    <div>{selectedOrderDetails.pickupAddress}</div>
                    <div style={{ color: 'var(--sd-muted)' }}>{selectedOrderDetails.pickupCity}</div>
                  </div>
                  <div>
                    <strong>Dropoff To:</strong>
                    <div>{selectedOrderDetails.dropoffAddress}</div>
                    <div style={{ color: 'var(--sd-muted)' }}>{selectedOrderDetails.dropoffCity}</div>
                  </div>
                  <div>
                    <strong>Recipient:</strong>
                    <div>{selectedOrderDetails.recipientName}</div>
                    <div style={{ color: 'var(--sd-muted)' }}>{selectedOrderDetails.recipientPhone}</div>
                  </div>
                  <div>
                    <strong>Fee & Weight:</strong>
                    <div>LKR {parseFloat(selectedOrderDetails.fee).toFixed(2)}</div>
                    <div style={{ color: 'var(--sd-muted)' }}>{selectedOrderDetails.weightKg} kg ({selectedOrderDetails.serviceType})</div>
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                {selectedOrderDetails.cancellable && (
                  <button
                    type="button"
                    className="btn btn-danger"
                    onClick={() => {
                      handleCancelOrder(selectedOrderDetails.trackingNumber);
                      setSelectedOrderDetails(null);
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
      </main>
    </div>
  );
}
