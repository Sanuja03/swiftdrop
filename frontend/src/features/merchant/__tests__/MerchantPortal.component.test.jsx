import { expect, it, describe, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MerchantPortal from '../pages/MerchantPortal';
import { merchantApi } from '../services/merchantApi';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ user: { role: 'ROLE_MERCHANT', email: 'merchant@store.lk' } }),
}));

vi.mock('../services/merchantApi', () => ({
  merchantApi: {
    getProfile: vi.fn(),
    getStats: vi.fn(),
    getOrders: vi.fn(),
    getActiveZones: vi.fn(),
    calculateFee: vi.fn(),
    createOrder: vi.fn(),
    cancelOrder: vi.fn(),
    regenerateApiKey: vi.fn(),
    updateWebhook: vi.fn(),
    getWebhookConfig: vi.fn(),
    rotateWebhookSecret: vi.fn(),
    testWebhook: vi.fn(),
    getWebhookLogs: vi.fn(),
    registerMerchant: vi.fn(),
    listAdminMerchants: vi.fn(),
    approveMerchant: vi.fn(),
  },
}));

vi.mock('@/features/notifications/pages/MyNotifications', () => ({
  default: () => <div data-testid="notifications-panel">Notifications List</div>,
}));

describe('MerchantPortal Component Tests', () => {
  const mockProfile = {
    id: 'merchant-uuid-1',
    businessName: 'Apex Fashion Store',
    registrationNumber: 'PV-84920',
    contactPhone: '0771234567',
    businessAddress: '100 Galle Road, Colombo 03',
    registrationStatus: 'APPROVED',
    apiKey: 'sdp_live_abc123xyz789livekey',
    webhookUrl: 'https://apexfashion.lk/webhook',
  };

  const mockStats = {
    totalOrders: 42,
    activeShipments: 14,
    deliveredOrders: 25,
    pendingOrders: 3,
    cancelledOrders: 2,
  };

  const mockOrders = [
    {
      id: 'ord-1',
      trackingNumber: 'SDP-20261120-X8J2P4',
      recipientName: 'Kasun Perera',
      recipientPhone: '0779876543',
      dropoffAddress: '12 Kandy Road',
      dropoffCity: 'Kandy',
      pickupAddress: '100 Galle Rd',
      pickupCity: 'Colombo',
      serviceType: 'EXPRESS',
      weightKg: 2.5,
      fee: 1250.0,
      status: 'CREATED',
      statusLabel: 'Pending Pickup',
      cancellable: true,
      createdAt: '2026-11-20T10:00:00Z',
    },
    {
      id: 'ord-2',
      trackingNumber: 'SDP-20261120-M3K9P1',
      recipientName: 'Nimali Silva',
      recipientPhone: '0712345678',
      dropoffAddress: '55 Beach Rd',
      dropoffCity: 'Galle',
      pickupAddress: '100 Galle Rd',
      pickupCity: 'Colombo',
      serviceType: 'STANDARD',
      weightKg: 1.0,
      fee: 750.0,
      status: 'PICKED_UP',
      statusLabel: 'Picked Up by Courier',
      cancellable: false,
      createdAt: '2026-11-20T08:30:00Z',
    },
  ];

  const mockZones = [
    { id: '1', name: 'Colombo', code: 'CMB' },
    { id: '2', name: 'Gampaha', code: 'GMP' },
    { id: '3', name: 'Kandy', code: 'KDY' },
    { id: '4', name: 'Galle', code: 'GLE' },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    merchantApi.getProfile.mockResolvedValue(mockProfile);
    merchantApi.getWebhookConfig.mockResolvedValue({ url: mockProfile.webhookUrl, configured: true });
    merchantApi.updateWebhook.mockResolvedValue({ url: mockProfile.webhookUrl, configured: true });
    merchantApi.getStats.mockResolvedValue(mockStats);
    merchantApi.getOrders.mockResolvedValue(mockOrders);
    merchantApi.getActiveZones.mockResolvedValue(mockZones);
    merchantApi.calculateFee.mockResolvedValue({
      serviceType: 'STANDARD',
      weightKg: 2.0,
      pickupCity: 'Colombo',
      dropoffCity: 'Kandy',
      baseFee: 500.0,
      weightCharge: 200.0,
      zoneSurcharge: 250.0,
      totalFee: 950.0,
      currency: 'LKR',
      estimatedDeliveryTime: '1 - 3 business days',
    });
    merchantApi.getWebhookLogs.mockResolvedValue([]);
  });

  it('1. Renders Merchant Dashboard with all 5 real-time KPI metric tracking cards', async () => {
    render(
      <MemoryRouter>
        <MerchantPortal />
      </MemoryRouter>
    );

    expect(await screen.findByText(/Merchant Analytics & Performance/)).toBeInTheDocument();
    expect(screen.getByText('Apex Fashion Store')).toBeInTheDocument();

    // Check KPI cards
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(screen.getByText('Total Orders')).toBeInTheDocument();

    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getAllByText('Pending Pickup')[0]).toBeInTheDocument();

    expect(screen.getByText('14')).toBeInTheDocument();
    expect(screen.getByText('Active Shipments')).toBeInTheDocument();

    expect(screen.getByText('25')).toBeInTheDocument();
    expect(screen.getByText('Delivered Orders')).toBeInTheDocument();

    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('Cancelled Orders')).toBeInTheDocument();
  });

  it('2. Navigates to Fee Calculator tab and calculates live quotes with active zone matrix', async () => {
    render(
      <MemoryRouter>
        <MerchantPortal />
      </MemoryRouter>
    );

    const calcTabBtn = (await screen.findAllByRole('button', { name: /Fee Calculator/ }))[0];
    fireEvent.click(calcTabBtn);

    expect(await screen.findByText(/Instant Pricing & Delivery Fee Calculator/)).toBeInTheDocument();
    expect(screen.getByText(/Live Fee Breakdown/)).toBeInTheDocument();
    expect(await screen.findByText(/LKR 950.00/)).toBeInTheDocument();

    // Test clicking "Book Parcel with this Quote"
    const bookWithQuoteBtn = screen.getByRole('button', { name: /Book Parcel with this Quote/ });
    fireEvent.click(bookWithQuoteBtn);

    expect(screen.getByText(/Manual Parcel Booking/)).toBeInTheDocument();
  });

  it('3. Navigates to My Orders and handles safe cancellation for CREATED orders', async () => {
    merchantApi.cancelOrder.mockResolvedValue({ id: 'ord-1', status: 'CANCELLED' });

    render(
      <MemoryRouter>
        <MerchantPortal />
      </MemoryRouter>
    );

    const ordersTabBtn = (await screen.findAllByRole('button', { name: /My Orders/ }))[0];
    fireEvent.click(ordersTabBtn);

    expect(await screen.findByText(/Order Lifecycle & Live Management/)).toBeInTheDocument();
    expect(screen.getByText('SDP-20261120-X8J2P4')).toBeInTheDocument();
    expect(screen.getByText('SDP-20261120-M3K9P1')).toBeInTheDocument();

    // The CREATED order has Cancel button
    const cancelBtn = screen.getAllByRole('button', { name: 'Cancel' })[0];
    fireEvent.click(cancelBtn);

    // Cancel modal appears explaining courier de-allocation
    expect(await screen.findByText(/Confirm Order Cancellation/)).toBeInTheDocument();
    expect(screen.getByText(/Courier De-allocation Guarantee/)).toBeInTheDocument();

    const confirmCancelBtn = screen.getByRole('button', { name: /Confirm Single-Click Cancellation/ });
    fireEvent.click(confirmCancelBtn);

    await waitFor(() => {
      expect(merchantApi.cancelOrder).toHaveBeenCalledWith('merchant-uuid-1', 'SDP-20261120-X8J2P4');
    });
  });

  it('4. Confirms streamlined merchant navigation without Developer API tab', async () => {
    render(
      <MemoryRouter>
        <MerchantPortal />
      </MemoryRouter>
    );

    // Confirms Developer API tab is removed for merchant user experience
    expect(screen.queryByRole('button', { name: /Developer API/ })).toBeNull();
    // Confirms core tabs are present
    expect(screen.getAllByRole('button', { name: /Dashboard/ })[0]).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Fee Calculator/ })[0]).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /My Orders/ })[0]).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Book Delivery/ })[0]).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Store Integration/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Business Profile/ })).toBeInTheDocument();
  });

  it('shows only a newly generated secret and preserves the business profile on save', async () => {
    merchantApi.updateWebhook.mockResolvedValue({ url: mockProfile.webhookUrl, configured: true, secret: 'whsec_new_once' });
    render(<MemoryRouter><MerchantPortal /></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: /Store Integration/ }));
    expect(await screen.findByLabelText('New webhook signing secret')).toHaveValue('');
    expect(screen.getByLabelText('New webhook signing secret')).toHaveAttribute('readonly');
    fireEvent.click(screen.getByRole('button', { name: /Save Settings/ }));
    await waitFor(() => expect(screen.getByLabelText('New webhook signing secret')).toHaveValue('whsec_new_once'));
    expect(merchantApi.updateWebhook).toHaveBeenCalledWith(mockProfile.id, { url: mockProfile.webhookUrl });
    fireEvent.click(screen.getByRole('button', { name: /Business Profile/ }));
    expect(await screen.findByText('PV-84920')).toBeInTheDocument();
    expect(screen.getByText('100 Galle Road, Colombo 03')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Store Integration/ }));
    merchantApi.updateWebhook.mockResolvedValue({ url: mockProfile.webhookUrl, configured: true });
    fireEvent.click(screen.getByRole('button', { name: /Save Settings/ }));
    await waitFor(() => expect(screen.getByLabelText('New webhook signing secret')).toHaveValue(''));
  });

  it('requires explicit confirmation before rotating a signing secret', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    merchantApi.rotateWebhookSecret.mockResolvedValue({ url: mockProfile.webhookUrl, configured: true, secret: 'whsec_rotated_once' });
    render(<MemoryRouter><MerchantPortal /></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: /Store Integration/ }));
    const rotate = await screen.findByRole('button', { name: 'Rotate signing secret' });
    await waitFor(() => expect(rotate).not.toBeDisabled());
    fireEvent.click(rotate);
    expect(merchantApi.rotateWebhookSecret).not.toHaveBeenCalled();
    confirm.mockReturnValue(true);
    fireEvent.click(rotate);
    await waitFor(() => expect(screen.getByLabelText('New webhook signing secret')).toHaveValue('whsec_rotated_once'));
    expect(merchantApi.rotateWebhookSecret).toHaveBeenCalledTimes(1);
    confirm.mockRestore();
  });

  it('renders the delivery engine status, response code and payload in history', async () => {
    merchantApi.getWebhookLogs.mockResolvedValue([{ id: 'delivery-1', trackingNumber: 'SDP-TEST',
      eventType: 'parcel.status_changed', endpointUrl: mockProfile.webhookUrl, status: 'DELIVERED',
      lastResponseCode: 204, lastError: null, attemptCount: 2, createdAt: '2026-11-21T12:00:00Z',
      payload: '{"eventId":"event-1"}' }]);
    render(<MemoryRouter><MerchantPortal /></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: /Store Integration/ }));
    expect(await screen.findByText('DELIVERED')).toBeInTheDocument();
    expect(screen.getByText('204')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Inspect' }));
    expect(await screen.findByText(/"eventId": "event-1"/)).toBeInTheDocument();
  });

  it('5. Navigates to Store Integration tab and triggers live connection test', async () => {
    merchantApi.testWebhook.mockResolvedValue({
      success: true,
      statusCode: 200,
      message: 'Ping succeeded (HTTP 200)',
      latencyMs: 84,
      responseBody: '{"ok":true}',
    });

    render(
      <MemoryRouter>
        <MerchantPortal />
      </MemoryRouter>
    );

    const storeTabBtn = await screen.findByRole('button', { name: /Store Integration/ });
    fireEvent.click(storeTabBtn);

    expect(await screen.findByText(/Store Connection Settings/)).toBeInTheDocument();
    expect(screen.getByDisplayValue('https://apexfashion.lk/webhook')).toBeInTheDocument();

    // Trigger Test Connection
    const testBtn = screen.getByRole('button', { name: /Test Connection/ });
    fireEvent.click(testBtn);

    await waitFor(() => {
      expect(merchantApi.testWebhook).toHaveBeenCalledWith('merchant-uuid-1', 'https://apexfashion.lk/webhook');
    });

    expect(await screen.findByText(/Connection Test Successful/)).toBeInTheDocument();
    expect(screen.getByText(/Response Time: 84 ms/)).toBeInTheDocument();
  });
});
