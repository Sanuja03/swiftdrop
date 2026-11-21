import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import CourierWorkloadPage from '../pages/CourierWorkloadPage'
import WorkloadCard from '../components/WorkloadCard'
import * as dispatchApi from '../api/dispatchApi'

vi.mock('../api/dispatchApi')

const mockWorkloads = [
  {
    courierId: 'courier-001',
    courierName: 'Kasun Bandara',
    courierEmail: 'kasun@swiftdrop.lk',
    courierPhone: '0771234567',
    activeAssignmentsCount: 0,
    workloadStatus: 'AVAILABLE',
  },
  {
    courierId: 'courier-002',
    courierName: 'Nuwan Pradeep',
    courierEmail: 'nuwan@swiftdrop.lk',
    courierPhone: '0714567890',
    activeAssignmentsCount: 2,
    workloadStatus: 'MODERATE',
  },
  {
    courierId: 'courier-003',
    courierName: 'Ruwan Kumara',
    courierEmail: 'ruwan@swiftdrop.lk',
    courierPhone: '0789998877',
    activeAssignmentsCount: 5,
    workloadStatus: 'BUSY',
  },
  {
    courierId: 'courier-004',
    courierName: 'Sunil Shantha',
    courierEmail: 'sunil@swiftdrop.lk',
    courierPhone: null,
    activeAssignmentsCount: 0,
    workloadStatus: 'AVAILABLE',
  },
]

describe('Courier Workload Page & WorkloadCard - Component Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    dispatchApi.getCourierWorkloads.mockResolvedValue(mockWorkloads)
  })

  it('1. Renders loading indicator initially, then displays workloads and summary cards', async () => {
    render(
      <MemoryRouter>
        <CourierWorkloadPage />
      </MemoryRouter>
    )

    // Initially loading
    expect(screen.getByTestId('workload-loading')).toBeInTheDocument()

    // Waits for workloads to render
    await waitFor(() => {
      expect(screen.getByText('Kasun Bandara')).toBeInTheDocument()
    })

    // Check header
    expect(screen.getByRole('heading', { name: 'Courier Workloads' })).toBeInTheDocument()

    // Check summary card values in Workload Overview: Total = 4, Available = 2, Moderate = 1, Busy = 1
    const overview = screen.getByLabelText('Workload Overview')
    expect(overview).toHaveTextContent('Total Couriers')
    expect(overview).toHaveTextContent('4')
    expect(overview).toHaveTextContent('Available (0 active)')
    expect(overview).toHaveTextContent('2')
    expect(overview).toHaveTextContent('Moderate (1-3 active)')
    expect(overview).toHaveTextContent('1')
    expect(overview).toHaveTextContent('Busy (> 3 active)')
  })

  it('2. Correctly renders AVAILABLE, MODERATE, and BUSY statuses with actual counts', async () => {
    render(
      <MemoryRouter>
        <CourierWorkloadPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Kasun Bandara')).toBeInTheDocument()
    })

    // Available courier
    const cardAvailable = screen.getByTestId('workload-card-courier-001')
    expect(cardAvailable).toHaveTextContent('AVAILABLE')
    expect(cardAvailable).toHaveTextContent('kasun@swiftdrop.lk')
    expect(cardAvailable).toHaveTextContent('0771234567')

    // Moderate courier
    const cardModerate = screen.getByTestId('workload-card-courier-002')
    expect(cardModerate).toHaveTextContent('MODERATE')
    expect(cardModerate).toHaveTextContent('nuwan@swiftdrop.lk')
    expect(cardModerate).toHaveTextContent('2')

    // Busy courier
    const cardBusy = screen.getByTestId('workload-card-courier-003')
    expect(cardBusy).toHaveTextContent('BUSY')
    expect(cardBusy).toHaveTextContent('ruwan@swiftdrop.lk')
    expect(cardBusy).toHaveTextContent('5')

    // Courier with no phone
    const cardNoPhone = screen.getByTestId('workload-card-courier-004')
    expect(cardNoPhone).toHaveTextContent('No phone number')
  })

  it('3. WorkloadCard standalone rendering verifies no invented capacity or percentages', () => {
    const singleWorkload = {
      courierId: 'test-courier-1',
      courierName: 'Dinesh Chandimal',
      courierEmail: 'dinesh@swiftdrop.lk',
      courierPhone: '0770001122',
      activeAssignmentsCount: 3,
      workloadStatus: 'MODERATE',
    }

    const { container } = render(<WorkloadCard workload={singleWorkload} />)

    expect(screen.getByText('Dinesh Chandimal')).toBeInTheDocument()
    expect(screen.getByText('dinesh@swiftdrop.lk')).toBeInTheDocument()
    expect(screen.getByText('0770001122')).toBeInTheDocument()
    expect(screen.getByTestId('active-assignments-count')).toHaveTextContent('3')
    expect(screen.getByTestId('workload-status-badge')).toHaveTextContent('MODERATE')

    // Verify absence of fake capacity indicators
    const fullText = container.textContent || ''
    expect(fullText).not.toContain('/ 5')
    expect(fullText).not.toContain('/ 10')
    expect(fullText).not.toContain('%')
    expect(fullText).not.toContain('Capacity')
    expect(container.querySelector('progress')).toBeNull()
  })

  it('4. Displays empty state message when no couriers are returned', async () => {
    dispatchApi.getCourierWorkloads.mockResolvedValueOnce([])

    render(
      <MemoryRouter>
        <CourierWorkloadPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByTestId('workload-empty-state')).toBeInTheDocument()
    })

    expect(screen.getByText('No Active Couriers Found')).toBeInTheDocument()
    expect(
      screen.getByText('There are currently no active couriers registered in the system.')
    ).toBeInTheDocument()
  })

  it('5. Displays error banner when fetching workloads fails and allows retry', async () => {
    dispatchApi.getCourierWorkloads.mockRejectedValueOnce(
      new Error('Connection to dispatch service timed out')
    )

    render(
      <MemoryRouter>
        <CourierWorkloadPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Failed to load courier workloads')).toBeInTheDocument()
    })

    expect(
      screen.getByText('Connection to dispatch service timed out')
    ).toBeInTheDocument()

    // Retry recovery
    dispatchApi.getCourierWorkloads.mockResolvedValueOnce(mockWorkloads)
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))

    await waitFor(() => {
      expect(screen.getByText('Kasun Bandara')).toBeInTheDocument()
    })
    expect(screen.queryByText('Failed to load courier workloads')).not.toBeInTheDocument()
  })

  it('6. Filters couriers by name and email via search input', async () => {
    render(
      <MemoryRouter>
        <CourierWorkloadPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Kasun Bandara')).toBeInTheDocument()
    })

    const searchInput = screen.getByRole('textbox', { name: 'Search couriers' })

    // Search by name
    fireEvent.change(searchInput, { target: { value: 'nuwan' } })

    expect(screen.getByText('Nuwan Pradeep')).toBeInTheDocument()
    expect(screen.queryByText('Kasun Bandara')).not.toBeInTheDocument()
    expect(screen.queryByText('Ruwan Kumara')).not.toBeInTheDocument()
    expect(screen.getByTestId('results-info')).toHaveTextContent('Showing 1 of 4 active couriers')

    // Search by email
    fireEvent.change(searchInput, { target: { value: 'ruwan@swiftdrop.lk' } })
    expect(screen.getByText('Ruwan Kumara')).toBeInTheDocument()
    expect(screen.queryByText('Nuwan Pradeep')).not.toBeInTheDocument()

    // Clear search
    const clearButton = screen.getByRole('button', { name: 'Clear search' })
    fireEvent.click(clearButton)

    expect(screen.getByText('Kasun Bandara')).toBeInTheDocument()
    expect(screen.getByText('Nuwan Pradeep')).toBeInTheDocument()
  })

  it('7. Filters couriers by workload status tabs (AVAILABLE, MODERATE, BUSY)', async () => {
    render(
      <MemoryRouter>
        <CourierWorkloadPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Kasun Bandara')).toBeInTheDocument()
    })

    // Click 'Available' tab
    fireEvent.click(screen.getByRole('button', { name: /Available \(2\)/i }))
    expect(screen.getByText('Kasun Bandara')).toBeInTheDocument()
    expect(screen.getByText('Sunil Shantha')).toBeInTheDocument()
    expect(screen.queryByText('Nuwan Pradeep')).not.toBeInTheDocument()
    expect(screen.queryByText('Ruwan Kumara')).not.toBeInTheDocument()

    // Click 'Moderate' tab
    fireEvent.click(screen.getByRole('button', { name: /Moderate \(1\)/i }))
    expect(screen.getByText('Nuwan Pradeep')).toBeInTheDocument()
    expect(screen.queryByText('Kasun Bandara')).not.toBeInTheDocument()

    // Click 'Busy' tab
    fireEvent.click(screen.getByRole('button', { name: /Busy \(1\)/i }))
    expect(screen.getByText('Ruwan Kumara')).toBeInTheDocument()
    expect(screen.queryByText('Nuwan Pradeep')).not.toBeInTheDocument()

    // Return to 'All' tab
    fireEvent.click(screen.getByRole('button', { name: /All \(4\)/i }))
    expect(screen.getByText('Kasun Bandara')).toBeInTheDocument()
    expect(screen.getByText('Ruwan Kumara')).toBeInTheDocument()
  })

  it('8. Shows no-matches empty state when search finds no couriers, and allows clearing filters', async () => {
    render(
      <MemoryRouter>
        <CourierWorkloadPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Kasun Bandara')).toBeInTheDocument()
    })

    const searchInput = screen.getByRole('textbox', { name: 'Search couriers' })
    fireEvent.change(searchInput, { target: { value: 'NonExistentCourierXYZ' } })

    expect(screen.getByTestId('workload-no-matches')).toBeInTheDocument()
    expect(screen.getByText('No Couriers Match Your Filter')).toBeInTheDocument()

    // Click clear filters button
    fireEvent.click(screen.getByRole('button', { name: 'Clear Filters' }))
    expect(screen.getByText('Kasun Bandara')).toBeInTheDocument()
  })

  it('9. Manual refresh triggers getCourierWorkloads API call', async () => {
    render(
      <MemoryRouter>
        <CourierWorkloadPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Kasun Bandara')).toBeInTheDocument()
    })

    expect(dispatchApi.getCourierWorkloads).toHaveBeenCalledTimes(1)

    // Click Refresh button
    const refreshButton = screen.getByRole('button', { name: 'Refresh' })
    fireEvent.click(refreshButton)

    await waitFor(() => {
      expect(dispatchApi.getCourierWorkloads).toHaveBeenCalledTimes(2)
    })
  })
})
