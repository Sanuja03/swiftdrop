// SwiftDrop Merchant API Service Client
const API_BASE = '/api/v1';

export const merchantApi = {
  // Merchant Profile & Stats
  async getProfile(merchantId) {
    const url = merchantId ? `${API_BASE}/merchant/profile?merchantId=${merchantId}` : `${API_BASE}/merchant/profile`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Failed to load profile: ${res.statusText}`);
    return res.json();
  },

  async getStats(merchantId) {
    const url = merchantId ? `${API_BASE}/merchant/stats?merchantId=${merchantId}` : `${API_BASE}/merchant/stats`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Failed to load stats: ${res.statusText}`);
    return res.json();
  },

  // Orders Management
  async getOrders(merchantId, status, search) {
    let url = `${API_BASE}/merchant/orders?`;
    const params = new URLSearchParams();
    if (merchantId) params.append('merchantId', merchantId);
    if (status && status !== 'ALL') params.append('status', status);
    if (search) params.append('search', search);

    const res = await fetch(url + params.toString());
    if (!res.ok) throw new Error(`Failed to load orders: ${res.statusText}`);
    return res.json();
  },

  async createOrder(merchantId, orderData) {
    const url = merchantId ? `${API_BASE}/merchant/orders?merchantId=${merchantId}` : `${API_BASE}/merchant/orders`;
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(orderData),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || 'Failed to create order');
    }
    return data;
  },

  async cancelOrder(merchantId, trackingNumber) {
    const url = merchantId
      ? `${API_BASE}/merchant/orders/${trackingNumber}/cancel?merchantId=${merchantId}`
      : `${API_BASE}/merchant/orders/${trackingNumber}/cancel`;
    const res = await fetch(url, {
      method: 'POST',
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || 'Cannot cancel order');
    }
    return data;
  },

  // Fee calculation preview
  async calculateFee(feeRequest) {
    const res = await fetch(`${API_BASE}/merchant/calculate-fee`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(feeRequest),
    });
    if (!res.ok) throw new Error('Fee calculation failed');
    return res.json();
  },

  // Webhook Integration & Testing
  async updateWebhook(merchantId, webhookData) {
    const url = merchantId ? `${API_BASE}/merchant/webhook?merchantId=${merchantId}` : `${API_BASE}/merchant/webhook`;
    const res = await fetch(url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(webhookData),
    });
    if (!res.ok) throw new Error('Failed to update webhook');
    return res.json();
  },

  async testWebhook(merchantId, testUrl) {
    const url = merchantId ? `${API_BASE}/merchant/webhook/test?merchantId=${merchantId}` : `${API_BASE}/merchant/webhook/test`;
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ webhookUrl: testUrl }),
    });
    return res.json();
  },

  async getWebhookLogs(merchantId) {
    const url = merchantId ? `${API_BASE}/merchant/webhook/logs?merchantId=${merchantId}` : `${API_BASE}/merchant/webhook/logs`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('Failed to load webhook logs');
    return res.json();
  },

  // API Key Management
  async regenerateApiKey(merchantId) {
    const url = merchantId ? `${API_BASE}/merchant/api-key/regenerate?merchantId=${merchantId}` : `${API_BASE}/merchant/api-key/regenerate`;
    const res = await fetch(url, {
      method: 'POST',
    });
    if (!res.ok) throw new Error('Failed to regenerate API key');
    return res.json();
  },

  // Registration & Admin Approval
  async registerMerchant(registrationData) {
    const res = await fetch(`${API_BASE}/merchant/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(registrationData),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || 'Registration failed');
    }
    return data;
  },

  async listAdminMerchants(status) {
    const url = status ? `${API_BASE}/admin/merchants?status=${status}` : `${API_BASE}/admin/merchants`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('Failed to load merchants');
    return res.json();
  },

  async approveMerchant(merchantId, notes) {
    const res = await fetch(`${API_BASE}/admin/merchants/${merchantId}/approve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ notes }),
    });
    return res.json();
  },

  async rejectMerchant(merchantId, notes) {
    const res = await fetch(`${API_BASE}/admin/merchants/${merchantId}/reject`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ notes }),
    });
    return res.json();
  },
};

