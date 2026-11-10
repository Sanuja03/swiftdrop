// SwiftDrop Merchant API Service Client
const API_BASE = '/api/v1';

// Initial Mock Demo Data for standalone frontend preview
const mockMerchant = {
  id: '7a35f29c-9821-4f1e-8488-84dc2d6a5c10',
  businessName: 'Colombo Electronics & Gadgets Ltd',
  registrationNumber: 'PV-89214-LK',
  contactPhone: '+94 77 123 4567',
  businessAddress: '45 Galle Road, Colombo 03',
  status: 'APPROVED',
  apiKey: 'sdp_live_9f82a17b8c6e430d892a017e29b1',
  webhookUrl: 'https://api.colomboelectronics.lk/webhooks/swiftdrop',
  webhookSecret: 'whsec_f8902a7b6c3d4e818902cba6120',
};

let mockOrders = [
  {
    id: 'ord-101',
    trackingNumber: 'SDP7845621',
    merchantId: mockMerchant.id,
    merchantBusinessName: mockMerchant.businessName,
    senderName: 'Colombo Electronics',
    recipientName: 'Kasun Perera',
    recipientPhone: '+94 71 234 5678',
    pickupAddress: '45 Galle Road, Colombo 03',
    pickupCity: 'Colombo',
    dropoffAddress: '12 Kandy Road, Peradeniya',
    dropoffCity: 'Kandy',
    serviceType: 'EXPRESS',
    weightKg: 2.5,
    fee: 805.0,
    status: 'IN_TRANSIT',
    createdAt: new Date(Date.now() - 3600000 * 5).toISOString(),
    updatedAt: new Date(Date.now() - 3600000 * 2).toISOString(),
  },
  {
    id: 'ord-102',
    trackingNumber: 'SDP8912304',
    merchantId: mockMerchant.id,
    merchantBusinessName: mockMerchant.businessName,
    senderName: 'Colombo Electronics',
    recipientName: 'Nimali Fernando',
    recipientPhone: '+94 77 345 6789',
    pickupAddress: '45 Galle Road, Colombo 03',
    pickupCity: 'Colombo',
    dropoffAddress: '78 Marine Drive, Colombo 04',
    dropoffCity: 'Colombo',
    serviceType: 'SAME_DAY',
    weightKg: 1.0,
    fee: 850.0,
    status: 'CREATED',
    createdAt: new Date(Date.now() - 3600000 * 1).toISOString(),
    updatedAt: new Date(Date.now() - 3600000 * 1).toISOString(),
  },
  {
    id: 'ord-103',
    trackingNumber: 'SDP4501928',
    merchantId: mockMerchant.id,
    merchantBusinessName: mockMerchant.businessName,
    senderName: 'Colombo Electronics',
    recipientName: 'Ruwan Jayasinghe',
    recipientPhone: '+94 76 987 6543',
    pickupAddress: '45 Galle Road, Colombo 03',
    pickupCity: 'Colombo',
    dropoffAddress: '5 Main Street, Galle',
    dropoffCity: 'Galle',
    serviceType: 'STANDARD',
    weightKg: 3.0,
    fee: 632.5,
    status: 'DELIVERED',
    createdAt: new Date(Date.now() - 86400000 * 2).toISOString(),
    updatedAt: new Date(Date.now() - 86400000 * 1).toISOString(),
  },
];

let mockWebhookLogs = [
  {
    id: 'wh-1',
    event: 'PARCEL_STATUS_UPDATED',
    targetUrl: mockMerchant.webhookUrl,
    payloadJson: JSON.stringify({ event: 'PARCEL_STATUS_UPDATED', trackingNumber: 'SDP7845621', status: 'IN_TRANSIT' }),
    responseStatusCode: 200,
    deliveryStatus: 'SUCCESS',
    retryCount: 0,
    createdAt: new Date(Date.now() - 3600000 * 2).toISOString(),
  },
  {
    id: 'wh-2',
    event: 'ORDER_CREATED',
    targetUrl: mockMerchant.webhookUrl,
    payloadJson: JSON.stringify({ event: 'ORDER_CREATED', trackingNumber: 'SDP8912304', status: 'CREATED' }),
    responseStatusCode: 200,
    deliveryStatus: 'SUCCESS',
    retryCount: 0,
    createdAt: new Date(Date.now() - 3600000 * 1).toISOString(),
  },
];

let mockAdminMerchants = [
  {
    id: mockMerchant.id,
    businessName: mockMerchant.businessName,
    registrationNumber: mockMerchant.registrationNumber,
    contactPhone: mockMerchant.contactPhone,
    businessAddress: mockMerchant.businessAddress,
    status: 'APPROVED',
    createdAt: new Date(Date.now() - 86400000 * 10).toISOString(),
  },
  {
    id: '49281a8b-3312-45e1-88f1-aa890123ef45',
    businessName: 'Lanka Herbal Products',
    registrationNumber: 'PV-10928-LK',
    contactPhone: '+94 70 555 4321',
    businessAddress: '19 Temple Road, Kalutara',
    status: 'PENDING',
    createdAt: new Date(Date.now() - 3600000 * 12).toISOString(),
  },
];

function calcFeeDemo(req) {
  let baseRate = 350.0;
  if (req.serviceType === 'EXPRESS') baseRate = 550.0;
  else if (req.serviceType === 'SAME_DAY') baseRate = 850.0;

  const weight = req.weightKg || 1.0;
  let extraWeightFee = 0.0;
  if (weight > 1.0) {
    extraWeightFee = (weight - 1.0) * 100.0;
  }

  let subtotal = baseRate + extraWeightFee;
  const isIntercity = req.pickupCity && req.dropoffCity &&
    req.pickupCity.trim().toLowerCase() !== req.dropoffCity.trim().toLowerCase();
  const intercitySurcharge = isIntercity ? subtotal * 0.15 : 0.0;
  const total = subtotal + intercitySurcharge;

  return {
    serviceType: req.serviceType,
    weightKg: weight,
    pickupCity: req.pickupCity,
    dropoffCity: req.dropoffCity,
    baseFee: baseRate,
    extraWeightFee: extraWeightFee,
    intercitySurcharge: intercitySurcharge,
    totalFee: Math.round(total * 100.0) / 100.0,
  };
}

export const merchantApi = {
  async getProfile(merchantId) {
    try {
      const url = merchantId ? `${API_BASE}/merchant/profile?merchantId=${merchantId}` : `${API_BASE}/merchant/profile`;
      const res = await fetch(url);
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    return mockMerchant;
  },

  async getStats(merchantId) {
    try {
      const url = merchantId ? `${API_BASE}/merchant/stats?merchantId=${merchantId}` : `${API_BASE}/merchant/stats`;
      const res = await fetch(url);
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    return {
      totalOrders: mockOrders.length,
      activeShipments: mockOrders.filter((o) => ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'].includes(o.status)).length,
      deliveredOrders: mockOrders.filter((o) => o.status === 'DELIVERED').length,
      pendingOrders: mockOrders.filter((o) => o.status === 'CREATED').length,
      cancelledOrders: mockOrders.filter((o) => o.status === 'CANCELLED').length,
    };
  },

  async getOrders(merchantId, status, search) {
    try {
      let url = `${API_BASE}/merchant/orders?`;
      const params = new URLSearchParams();
      if (merchantId) params.append('merchantId', merchantId);
      if (status && status !== 'ALL') params.append('status', status);
      if (search) params.append('search', search);

      const res = await fetch(url + params.toString());
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    return mockOrders.filter((o) => {
      if (status && status !== 'ALL' && o.status !== status) return false;
      if (search) {
        const q = search.toLowerCase();
        return (
          o.trackingNumber.toLowerCase().includes(q) ||
          o.recipientName.toLowerCase().includes(q) ||
          o.dropoffCity.toLowerCase().includes(q)
        );
      }
      return true;
    });
  },

  async createOrder(merchantId, orderData) {
    try {
      const url = merchantId ? `${API_BASE}/merchant/orders?merchantId=${merchantId}` : `${API_BASE}/merchant/orders`;
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderData),
      });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    const feeObj = calcFeeDemo(orderData);
    const trackingNum = 'SDP' + Math.floor(1000000 + Math.random() * 9000000);
    const newOrd = {
      id: 'ord-' + Date.now(),
      trackingNumber: trackingNum,
      merchantId: mockMerchant.id,
      merchantBusinessName: mockMerchant.businessName,
      senderName: mockMerchant.businessName,
      recipientName: orderData.recipientName,
      recipientPhone: orderData.recipientPhone,
      pickupAddress: orderData.pickupAddress,
      pickupCity: orderData.pickupCity,
      dropoffAddress: orderData.dropoffAddress,
      dropoffCity: orderData.dropoffCity,
      serviceType: orderData.serviceType,
      weightKg: parseFloat(orderData.weightKg) || 1.0,
      fee: feeObj.totalFee,
      status: 'CREATED',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    mockOrders = [newOrd, ...mockOrders];
    return newOrd;
  },

  async cancelOrder(merchantId, trackingNumber) {
    try {
      const url = merchantId
        ? `${API_BASE}/merchant/orders/${trackingNumber}/cancel?merchantId=${merchantId}`
        : `${API_BASE}/merchant/orders/${trackingNumber}/cancel`;
      const res = await fetch(url, { method: 'POST' });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    const target = mockOrders.find((o) => o.trackingNumber === trackingNumber);
    if (!target) throw new Error(`Order ${trackingNumber} not found.`);
    if (target.status !== 'CREATED') {
      throw new Error(`Order ${trackingNumber} cannot be cancelled because its status is ${target.status}. Only CREATED orders can be cancelled.`);
    }
    target.status = 'CANCELLED';
    target.updatedAt = new Date().toISOString();
    return target;
  },

  async calculateFee(feeRequest) {
    try {
      const res = await fetch(`${API_BASE}/merchant/calculate-fee`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(feeRequest),
      });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    return calcFeeDemo(feeRequest);
  },

  async updateWebhook(merchantId, webhookData) {
    try {
      const url = merchantId ? `${API_BASE}/merchant/webhook?merchantId=${merchantId}` : `${API_BASE}/merchant/webhook`;
      const res = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(webhookData),
      });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    mockMerchant.webhookUrl = webhookData.webhookUrl;
    mockMerchant.webhookSecret = webhookData.webhookSecret;
    return mockMerchant;
  },

  async testWebhook(merchantId, testUrl) {
    try {
      const url = merchantId ? `${API_BASE}/merchant/webhook/test?merchantId=${merchantId}` : `${API_BASE}/merchant/webhook/test`;
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ webhookUrl: testUrl }),
      });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    const log = {
      id: 'wh-test-' + Date.now(),
      event: 'PING_TEST',
      targetUrl: testUrl || mockMerchant.webhookUrl,
      payloadJson: JSON.stringify({ event: 'PING_TEST', timestamp: new Date().toISOString() }),
      responseStatusCode: 200,
      deliveryStatus: 'SUCCESS',
      retryCount: 0,
      createdAt: new Date().toISOString(),
    };
    mockWebhookLogs = [log, ...mockWebhookLogs];
    return {
      success: true,
      statusCode: 200,
      message: 'Webhook test event successfully delivered (HTTP 200 OK).',
    };
  },

  async getWebhookLogs(merchantId) {
    try {
      const url = merchantId ? `${API_BASE}/merchant/webhook/logs?merchantId=${merchantId}` : `${API_BASE}/merchant/webhook/logs`;
      const res = await fetch(url);
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    return mockWebhookLogs;
  },

  async regenerateApiKey(merchantId) {
    try {
      const url = merchantId ? `${API_BASE}/merchant/api-key/regenerate?merchantId=${merchantId}` : `${API_BASE}/merchant/api-key/regenerate`;
      const res = await fetch(url, { method: 'POST' });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    const newKey = 'sdp_live_' + Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
    mockMerchant.apiKey = newKey;
    return mockMerchant;
  },

  async registerMerchant(registrationData) {
    try {
      const res = await fetch(`${API_BASE}/merchant/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(registrationData),
      });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    const newM = {
      id: 'm-' + Date.now(),
      businessName: registrationData.businessName,
      registrationNumber: registrationData.registrationNumber,
      contactPhone: registrationData.contactPhone,
      businessAddress: registrationData.businessAddress,
      status: 'PENDING',
      createdAt: new Date().toISOString(),
    };
    mockAdminMerchants = [newM, ...mockAdminMerchants];
    return newM;
  },

  async listAdminMerchants(status) {
    try {
      const url = status ? `${API_BASE}/admin/merchants?status=${status}` : `${API_BASE}/admin/merchants`;
      const res = await fetch(url);
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    return mockAdminMerchants.filter((m) => !status || m.status === status);
  },

  async approveMerchant(merchantId, notes) {
    try {
      const res = await fetch(`${API_BASE}/admin/merchants/${merchantId}/approve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ notes }),
      });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    const target = mockAdminMerchants.find((m) => m.id === merchantId);
    if (target) {
      target.status = 'APPROVED';
      target.apiKey = 'sdp_live_' + Math.random().toString(36).substring(2, 15);
    }
    return target || mockMerchant;
  },

  async rejectMerchant(merchantId, notes) {
    try {
      const res = await fetch(`${API_BASE}/admin/merchants/${merchantId}/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ notes }),
      });
      if (res.ok) return await res.json();
    } catch {
      // Fallback
    }
    const target = mockAdminMerchants.find((m) => m.id === merchantId);
    if (target) {
      target.status = 'REJECTED';
    }
    return target;
  },
};
