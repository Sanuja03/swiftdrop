import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import DispatchManagementPage from '../pages/DispatchManagementPage'
import * as dispatchApi from '../api/dispatchApi'

vi.mock('../api/dispatchApi')

const mockParcels = [
  {
    id: 'parcel-101',
    trackingNumber: 'SDP1000001',
    senderName: 'Nimal Silva',
    recipientName: 'Kamal Perera',
    recipientPhone: '0771234567',
    pickupAddress: '12 Temple Rd',
    pickupCity: 'Colombo',
    dropoffAddress: '45 Lake View',
    dropoffCity: 'Kandy',
    serviceType: 'EXPRESS',
    weightKg: 2.5,
    fee: 550.0,
    status: 'CREATED',
    estimatedDeliveryAt: '2026-11-15T10:00:00Z',
    createdAt: '2026-11-14T08:00:00Z',
  },
  {
    id: 'parcel-102',
    trackingNumber: 'SDP1000002',
    senderName: 'Sunil Fernando',
    recipientName: 'Sita Kumari',
    recipientPhone: '0719876543',
    pickupAddress: '88 Galle Rd',
    pickupCity: 'Galle',
    dropoffAddress: '99 Main St',
    dropoffCity: 'Matara',
    serviceType: 'STANDARD',
    weightKg: 1.0,
    fee: 350.0,
    status: 'CREATED',
    estimatedDeliveryAt: '2026-11-16T12:00:00Z',
    createdAt: '2026-11-14T09:30:00Z',
  },
]

const mockCouriers = [
  {
    id: 'courier-201',
    fullName: 'Anura Kumara',
    email: 'anura@swiftdrop.lk',
    phone: '0701112233',
  },
  {
    id: 'courier-202',
    fullName: 'Saman Jayasuriya',
    email: 'saman@swiftdrop.lk',
    phone: '0724445566',
  },
]

describe('Dispatch Management Page & Assignment Modal - Component Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    dispatchApi.getUnassignedParcels.mockResolvedValue(mockParcels)
    dispatchApi.getActiveCouriers.mockResolvedValue(mockCouriers)
  })

  it('1. Renders loading state initially, then displays KPIs and unassigned parcels', async () => {
    render(
      <MemoryRouter>
        <DispatchManagementPage />
      </MemoryRouter>
    )

    // Initially loading
    expect(screen.getByText('Loading unassigned parcel queue...')).toBeInTheDocument()

    // Loaded
    await waitFor(() => {
      expect(screen.getByText('SDP1000001')).toBeInTheDocument()
      expect(screen.getByText('SDP1000002')).toBeInTheDocument()
    })

    // KPIs
    expect(screen.getByText('Unassigned Shipments')).toBeInTheDocument()
    expect(screen.getByText('Active Couriers')).toBeInTheDocument()
    expect(screen.getByText('Express & Priority')).toBeInTheDocument()

    // Parcel details
    expect(screen.getByText('Kamal Perera')).toBeInTheDocument()
    expect(screen.getByText('0771234567')).toBeInTheDocument()
    expect(screen.getByText('Colombo')).toBeInTheDocument()
    expect(screen.getByText('Kandy')).toBeInTheDocument()
    expect(screen.getByText('EXPRESS')).toBeInTheDocument()
    expect(screen.getByText('2.5 kg')).toBeInTheDocument()
  })

  it('2. Displays empty state when no unassigned parcels exist', async () => {
    dispatchApi.getUnassignedParcels.mockResolvedValue([])

    render(
      <MemoryRouter>
        <DispatchManagementPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('No unassigned parcels')).toBeInTheDocument()
      expect(
        screen.getByText(/There are currently no parcels waiting for courier assignment/)
      ).toBeInTheDocument()
    })
  })

  it('3. Displays error banner when fetching queue fails, and allows retry', async () => {
    dispatchApi.getUnassignedParcels.mockRejectedValue(new Error('Network connection timeout'))

    render(
      <MemoryRouter>
        <DispatchManagementPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Dispatch Queue Error')).toBeInTheDocument()
      expect(screen.getByText('Network connection timeout')).toBeInTheDocument()
    })

    // Click Retry
    dispatchApi.getUnassignedParcels.mockResolvedValue(mockParcels)
    const retryBtn = screen.getByRole('button', { name: 'Retry' })
    fireEvent.click(retryBtn)

    await waitFor(() => {
      expect(screen.getByText('SDP1000001')).toBeInTheDocument()
    })
  })

  it('4. Filters unassigned parcels by search query and service level', async () => {
    render(
      <MemoryRouter>
        <DispatchManagementPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('SDP1000001')).toBeInTheDocument()
      expect(screen.getByText('SDP1000002')).toBeInTheDocument()
    })

    // Search for "Galle"
    const searchInput = screen.getByLabelText('Filter unassigned parcels')
    fireEvent.change(searchInput, { target: { value: 'Galle' } })

    expect(screen.queryByText('SDP1000001')).not.toBeInTheDocument()
    expect(screen.getByText('SDP1000002')).toBeInTheDocument()

    // Reset search
    fireEvent.change(searchInput, { target: { value: '' } })
    expect(screen.getByText('SDP1000001')).toBeInTheDocument()

    // Filter by Service Type: STANDARD
    const serviceFilter = screen.getByLabelText('Filter by service level')
    fireEvent.change(serviceFilter, { target: { value: 'STANDARD' } })

    expect(screen.queryByText('SDP1000001')).not.toBeInTheDocument()
    expect(screen.getByText('SDP1000002')).toBeInTheDocument()
  })

  it('5. Opens Assign Courier modal when Assign button is clicked', async () => {
    render(
      <MemoryRouter>
        <DispatchManagementPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('SDP1000001')).toBeInTheDocument()
    })

    // Click Assign for the first parcel
    const assignButtons = screen.getAllByRole('button', { name: 'Assign' })
    fireEvent.click(assignButtons[0])

    // Modal opens
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Assign Courier' })).toBeInTheDocument()
    expect(screen.getAllByText('SDP1000001').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/Route:/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Select Active Courier/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Dispatcher Notes/)).toBeInTheDocument()
  })

  it('6. Closes Assign Courier modal when Cancel is clicked', async () => {
    render(
      <MemoryRouter>
        <DispatchManagementPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('SDP1000001')).toBeInTheDocument()
    })

    const assignButtons = screen.getAllByRole('button', { name: 'Assign' })
    fireEvent.click(assignButtons[0])
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    // Click Cancel
    const cancelBtn = screen.getByRole('button', { name: 'Cancel' })
    fireEvent.click(cancelBtn)

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  it('7. Validates that a courier must be selected before submission', async () => {
    render(
      <MemoryRouter>
        <DispatchManagementPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('SDP1000001')).toBeInTheDocument()
    })

    const assignButtons = screen.getAllByRole('button', { name: 'Assign' })
    fireEvent.click(assignButtons[0])

    // Click submit without selecting courier
    const submitBtn = screen.getByRole('button', { name: 'Confirm Assignment' })
    fireEvent.click(submitBtn)

    expect(
      screen.getByText('Please select a courier to assign this parcel.')
    ).toBeInTheDocument()
    expect(dispatchApi.assignCourier).not.toHaveBeenCalled()
  })

  it('8. Successfully assigns courier, displays success feedback, and reloads queue', async () => {
    dispatchApi.assignCourier.mockResolvedValue({
      id: 'assignment-901',
      parcelId: 'parcel-101',
      courierId: 'courier-201',
      status: 'ASSIGNED',
    })

    render(
      <MemoryRouter>
        <DispatchManagementPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('SDP1000001')).toBeInTheDocument()
    })

    const assignButtons = screen.getAllByRole('button', { name: 'Assign' })
    fireEvent.click(assignButtons[0])

    // Select courier
    const select = screen.getByLabelText(/Select Active Courier/)
    fireEvent.change(select, { target: { value: 'courier-201' } })

    // Add notes
    const notesInput = screen.getByLabelText(/Dispatcher Notes/)
    fireEvent.change(notesInput, { target: { value: 'Fragile package, deliver before 5pm' } })

    // Submit
    const submitBtn = screen.getByRole('button', { name: 'Confirm Assignment' })
    fireEvent.click(submitBtn)

    expect(dispatchApi.assignCourier).toHaveBeenCalledWith({
      parcelId: 'parcel-101',
      courierId: 'courier-201',
      notes: 'Fragile package, deliver before 5pm',
    })

    await waitFor(() => {
      expect(screen.getByText(/Assigned to Anura Kumara successfully/)).toBeInTheDocument()
    })

    // After success delay, modal closes and success banner appears on page
    await waitFor(
      () => {
        expect(screen.getByText(/Parcel SDP1000001 successfully assigned to courier/)).toBeInTheDocument()
      },
      { timeout: 2000 }
    )
  })

  it('9. Displays error feedback inside modal if assignment API fails', async () => {
    dispatchApi.assignCourier.mockRejectedValue(
      new Error('Courier has reached maximum active daily workload')
    )

    render(
      <MemoryRouter>
        <DispatchManagementPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('SDP1000001')).toBeInTheDocument()
    })

    const assignButtons = screen.getAllByRole('button', { name: 'Assign' })
    fireEvent.click(assignButtons[0])

    const select = screen.getByLabelText(/Select Active Courier/)
    fireEvent.change(select, { target: { value: 'courier-201' } })

    const submitBtn = screen.getByRole('button', { name: 'Confirm Assignment' })
    fireEvent.click(submitBtn)

    await waitFor(() => {
      expect(screen.getByText('Assignment Error')).toBeInTheDocument()
      expect(
        screen.getByText('Courier has reached maximum active daily workload')
      ).toBeInTheDocument()
    })
  })

  it('10. Prevents duplicate submissions while request is in progress', async () => {
    let resolveAssignment
    dispatchApi.assignCourier.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveAssignment = resolve
        })
    )

    render(
      <MemoryRouter>
        <DispatchManagementPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('SDP1000001')).toBeInTheDocument()
    })

    const assignButtons = screen.getAllByRole('button', { name: 'Assign' })
    fireEvent.click(assignButtons[0])

    const select = screen.getByLabelText(/Select Active Courier/)
    fireEvent.change(select, { target: { value: 'courier-201' } })

    const submitBtn = screen.getByRole('button', { name: 'Confirm Assignment' })
    fireEvent.click(submitBtn)

    // While submitting, button is disabled and shows Assigning...
    expect(screen.getByRole('button', { name: 'Assigning...' })).toBeDisabled()

    // Second click does nothing
    fireEvent.click(submitBtn)
    expect(dispatchApi.assignCourier).toHaveBeenCalledTimes(1)

    // Resolve promise
    resolveAssignment({ id: 'assign-1', status: 'ASSIGNED' })
  })
})
