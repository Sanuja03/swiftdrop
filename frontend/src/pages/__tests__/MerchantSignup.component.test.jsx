import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import SignupPage from '../SignupPage'
import { signupUser } from '@/services/api'

vi.mock('@/services/api', () => ({ signupUser: vi.fn() }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ loginWithGoogle: vi.fn() }) }))
vi.mock('@react-oauth/google', () => ({ GoogleLogin: () => null }))
vi.mock('motion/react', () => ({
  motion: {
    div: ({ children, className }) => <div className={className}>{children}</div>,
    button: ({ children, className, type, disabled, onClick }) => <button className={className} type={type} disabled={disabled} onClick={onClick}>{children}</button>,
  },
  AnimatePresence: ({ children }) => children,
}))

function fill(placeholder, value) {
  fireEvent.change(screen.getByPlaceholderText(placeholder), { target: { value } })
}

function renderSignup() {
  return render(<MemoryRouter><SignupPage /></MemoryRouter>)
}

describe('Merchant signup application details', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    signupUser.mockResolvedValue({ success: false, error: 'Test response' })
  })

  it('submits business details with the merchant account registration', async () => {
    const { container } = render(<MemoryRouter><SignupPage /></MemoryRouter>)
    fireEvent.click(screen.getByRole('button', { name: 'Business / Merchant' }))
    fill('Your business name', ' Test Shop ')
    fill('Your full name', 'Test Contact')
    fill('Business registration number', 'TEST-REG')
    expect(screen.getByPlaceholderText('Business registration number')).toBeRequired()
    fill('Full business address', ' Test address ')
    fill('name@example.com', 'test@example.test')
    fill('+94 70 000 0000', '0770000000')
    fill('8-16 characters', 'TestPassword123!')
    fill('Repeat password', 'TestPassword123!')
    fireEvent.submit(container.querySelector('form'))
    await waitFor(() => expect(signupUser).toHaveBeenCalledWith(expect.objectContaining({
      role: 'ROLE_MERCHANT', businessName: 'Test Shop', businessAddress: 'Test address',
      registrationNumber: 'TEST-REG', phone: '0770000000',
    })))
  }, 15000)

  it('does not send merchant-only fields for customer signup', async () => {
    const { container } = render(<MemoryRouter><SignupPage /></MemoryRouter>)
    fill('Your full name', 'Test Customer')
    fill('name@example.com', 'test@example.test')
    fill('+94 70 000 0000', '0770000000')
    fill('8-16 characters', 'TestPassword123!')
    fill('Repeat password', 'TestPassword123!')
    fireEvent.submit(container.querySelector('form'))
    await waitFor(() => expect(signupUser).toHaveBeenCalled())
    const [request] = signupUser.mock.calls[0]
    expect(request.role).toBe('ROLE_CUSTOMER')
    expect(request).not.toHaveProperty('businessName')
    expect(screen.queryByPlaceholderText('Full business address')).not.toBeInTheDocument()
  })

  it('rejects incomplete merchant details before sending a request', async () => {
    const { container } = render(<MemoryRouter><SignupPage /></MemoryRouter>)
    fireEvent.click(screen.getByRole('button', { name: 'Business / Merchant' }))
    fill('Your business name', 'Test Shop')
    fill('Your full name', 'Test Contact')
    fill('8-16 characters', 'TestPassword123!')
    fill('Repeat password', 'TestPassword123!')
    fireEvent.submit(container.querySelector('form'))
    expect(await screen.findByText('Please complete your business name, registration number, contact person, phone and business address.')).toBeInTheDocument()
    expect(signupUser).not.toHaveBeenCalled()
  })

  it('rejects invalid mobile numbers before calling signupUser', async () => {
    const { container } = renderSignup()
    fill('Your full name', 'Test Customer')
    fill('name@example.com', 'test@example.test')
    fill('+94 70 000 0000', '0000000000')
    fill('8-16 characters', 'TestPassword123!')
    fill('Repeat password', 'TestPassword123!')
    fireEvent.submit(container.querySelector('form'))
    expect(
      await screen.findByText(
        'Please enter a valid Sri Lankan mobile number (e.g., 077 123 4567 or +94 77 123 4567).'
      )
    ).toBeInTheDocument()
    expect(signupUser).not.toHaveBeenCalled()
  })

  it.each([
    ['Ab1!xyz', 'Password must be between 8 and 16 characters long.'],
    ['Abcdefgh1!1234567', 'Password must be between 8 and 16 characters long.'],
    ['Test Pass1!', 'Password must not contain spaces.'],
    ['testpassword1!', 'Password must include at least one uppercase letter, one lowercase letter, one number, and one special character.'],
    ['TESTPASSWORD1!', 'Password must include at least one uppercase letter, one lowercase letter, one number, and one special character.'],
    ['TestPassword!!', 'Password must include at least one uppercase letter, one lowercase letter, one number, and one special character.'],
    ['TestPassword12', 'Password must include at least one uppercase letter, one lowercase letter, one number, and one special character.'],
  ])('rejects invalid password "%s"', async (badPassword, expectedError) => {
    const { container } = renderSignup()
    fill('Your full name', 'Test Customer')
    fill('name@example.com', 'test@example.test')
    fill('+94 70 000 0000', '0770000000')
    fill('8-16 characters', badPassword)
    fill('Repeat password', badPassword)
    fireEvent.submit(container.querySelector('form'))
    expect(await screen.findByText(expectedError)).toBeInTheDocument()
    expect(signupUser).not.toHaveBeenCalled()
  })
})
