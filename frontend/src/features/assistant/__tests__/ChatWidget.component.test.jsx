import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ChatWidget from '../components/ChatWidget'
import ChatInput from '../components/ChatInput'
import ChatMessageList from '../components/ChatMessageList'
import ChatMessageBubble from '../components/ChatMessageBubble'
import * as assistantApi from '../api/assistantApi'

describe('React AI Assistant Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    sessionStorage.clear()
    localStorage.clear()
    localStorage.setItem('swiftdrop_token', 'test-jwt-token')
  })

  describe('ChatWidget lifecycle & interactions', () => {
    it('1. Widget is initially closed, displaying floating action launcher', () => {
      render(<ChatWidget />)
      expect(screen.getByRole('button', { name: /Open SwiftDrop AI Delivery Assistant/i })).toBeInTheDocument()
      expect(screen.queryByRole('dialog', { name: /SwiftDrop AI Assistant Chat/i })).not.toBeInTheDocument()
    })

    it('2. Clicking floating chat button opens the widget', async () => {
      vi.spyOn(assistantApi, 'createSession').mockResolvedValue({
        sessionId: 'session-test-123',
        createdAt: '2026-11-14T10:00:00Z',
      })

      render(<ChatWidget />)
      const launcher = screen.getByRole('button', { name: /Open SwiftDrop AI Delivery Assistant/i })
      await userEvent.click(launcher)

      expect(await screen.findByRole('dialog', { name: /SwiftDrop AI Assistant Chat/i })).toBeInTheDocument()
      expect(screen.getByText('SwiftDrop Assistant')).toBeInTheDocument()
    })

    it('3. Clicking close button closes the widget', async () => {
      vi.spyOn(assistantApi, 'createSession').mockResolvedValue({
        sessionId: 'session-test-123',
        createdAt: '2026-11-14T10:00:00Z',
      })

      render(<ChatWidget />)
      await userEvent.click(screen.getByRole('button', { name: /Open SwiftDrop AI Delivery Assistant/i }))
      expect(await screen.findByRole('dialog', { name: /SwiftDrop AI Assistant Chat/i })).toBeInTheDocument()

      const closeBtn = screen.getByRole('button', { name: /Close chat/i })
      await userEvent.click(closeBtn)

      expect(screen.queryByRole('dialog', { name: /SwiftDrop AI Assistant Chat/i })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Open SwiftDrop AI Delivery Assistant/i })).toBeInTheDocument()
    })

    it('4. Opening widget creates a backend session when none exists', async () => {
      const createSessionSpy = vi.spyOn(assistantApi, 'createSession').mockResolvedValue({
        sessionId: 'session-fresh-999',
        createdAt: '2026-11-14T10:00:00Z',
      })

      render(<ChatWidget />)
      await userEvent.click(screen.getByRole('button', { name: /Open SwiftDrop AI Delivery Assistant/i }))

      await waitFor(() => {
        expect(createSessionSpy).toHaveBeenCalledTimes(1)
        expect(sessionStorage.getItem('swiftdrop_assistant_session_id')).toBe('session-fresh-999')
      })
    })

    it('5. Existing session ID is restored from sessionStorage and history is loaded', async () => {
      sessionStorage.setItem('swiftdrop_assistant_session_id', 'session-existing-123')
      const getHistorySpy = vi.spyOn(assistantApi, 'getSessionHistory').mockResolvedValue([
        { id: '1', sender: 'USER', content: 'Where is my parcel SDP-1001?', createdAt: '2026-11-14T10:00:00Z' },
        { id: '2', sender: 'ASSISTANT', content: 'Your parcel is in Colombo.', createdAt: '2026-11-14T10:00:02Z' },
      ])

      render(<ChatWidget />)
      await userEvent.click(screen.getByRole('button', { name: /Open SwiftDrop AI Delivery Assistant/i }))

      await waitFor(() => {
        expect(getHistorySpy).toHaveBeenCalledWith('session-existing-123')
      })
      expect(await screen.findByText('Where is my parcel SDP-1001?')).toBeInTheDocument()
      expect(screen.getByText('Your parcel is in Colombo.')).toBeInTheDocument()
    })

    it('6. Invalid session (404) is cleared and replaced with a new backend session', async () => {
      sessionStorage.setItem('swiftdrop_assistant_session_id', 'session-expired')
      vi.spyOn(assistantApi, 'getSessionHistory').mockRejectedValue({ status: 404, message: 'Not found' })
      const createSessionSpy = vi.spyOn(assistantApi, 'createSession').mockResolvedValue({
        sessionId: 'session-recovered',
        createdAt: '2026-11-14T10:00:00Z',
      })

      render(<ChatWidget />)
      await userEvent.click(screen.getByRole('button', { name: /Open SwiftDrop AI Delivery Assistant/i }))

      await waitFor(() => {
        expect(createSessionSpy).toHaveBeenCalled()
        expect(sessionStorage.getItem('swiftdrop_assistant_session_id')).toBe('session-recovered')
      })
    })

    it('7. Authentication failure (401) displays appropriate friendly error message', async () => {
      vi.spyOn(assistantApi, 'createSession').mockRejectedValue({
        status: 401,
        message: 'Unauthorized',
      })

      render(<ChatWidget />)
      await userEvent.click(screen.getByRole('button', { name: /Open SwiftDrop AI Delivery Assistant/i }))

      expect(
        await screen.findByText('Your login session has expired. Please sign in again to continue.')
      ).toBeInTheDocument()
    })
  })

  describe('ChatInput interactions & validation', () => {
    it('1. Customer can type a message and send by clicking Send button', async () => {
      const onSend = vi.fn()
      render(<ChatInput onSendMessage={onSend} disabled={false} />)

      const textarea = screen.getByRole('textbox', { name: /Type your message/i })
      await userEvent.type(textarea, 'Track SDP-1001')
      expect(textarea).toHaveValue('Track SDP-1001')

      const sendBtn = screen.getByRole('button', { name: /Send message/i })
      await userEvent.click(sendBtn)

      expect(onSend).toHaveBeenCalledWith('Track SDP-1001')
      expect(textarea).toHaveValue('')
    })

    it('2. Pressing Enter submits the message', async () => {
      const onSend = vi.fn()
      render(<ChatInput onSendMessage={onSend} disabled={false} />)

      const textarea = screen.getByRole('textbox', { name: /Type your message/i })
      await userEvent.type(textarea, 'Hello assistant{Enter}')

      expect(onSend).toHaveBeenCalledWith('Hello assistant')
      expect(textarea).toHaveValue('')
    })

    it('3. Pressing Shift+Enter inserts a newline without submitting', async () => {
      const onSend = vi.fn()
      render(<ChatInput onSendMessage={onSend} disabled={false} />)

      const textarea = screen.getByRole('textbox', { name: /Type your message/i })
      await userEvent.type(textarea, 'Line 1{Shift>}{Enter}{/Shift}Line 2')

      expect(onSend).not.toHaveBeenCalled()
      expect(textarea.value).toContain('Line 1\nLine 2')
    })

    it('4. Empty and whitespace-only messages cannot be submitted', async () => {
      const onSend = vi.fn()
      render(<ChatInput onSendMessage={onSend} disabled={false} />)

      const sendBtn = screen.getByRole('button', { name: /Send message/i })
      expect(sendBtn).toBeDisabled()

      const textarea = screen.getByRole('textbox', { name: /Type your message/i })
      await userEvent.type(textarea, '   ')
      expect(sendBtn).toBeDisabled()

      await userEvent.type(textarea, '{Enter}')
      expect(onSend).not.toHaveBeenCalled()
    })

    it('5. Input and submit button are disabled while a request is in progress', () => {
      const onSend = vi.fn()
      render(<ChatInput onSendMessage={onSend} disabled={true} />)

      expect(screen.getByRole('textbox', { name: /Type your message/i })).toBeDisabled()
      expect(screen.getByRole('button', { name: /Send message/i })).toBeDisabled()
    })

    it('6. Textarea restricts message length to 1000 characters', async () => {
      render(<ChatInput onSendMessage={vi.fn()} disabled={false} />)
      const textarea = screen.getByRole('textbox', { name: /Type your message/i })
      expect(textarea).toHaveAttribute('maxLength', '1000')
    })
  })

  describe('ChatMessageList & ChatMessageBubble rendering', () => {
    it('1. Customer and assistant messages render with distinct styles', () => {
      const messages = [
        { id: '1', sender: 'USER', content: 'What is my parcel status?', createdAt: '2026-11-14T10:00:00Z' },
        { id: '2', sender: 'ASSISTANT', content: 'Your parcel is in transit.', createdAt: '2026-11-14T10:00:05Z' },
      ]

      render(<ChatMessageList messages={messages} loading={false} error={null} onSelectPrompt={vi.fn()} />)

      expect(screen.getByText('What is my parcel status?')).toBeInTheDocument()
      expect(screen.getByText('Your parcel is in transit.')).toBeInTheDocument()
    })

    it('2. Internal tool arguments and raw tool results are NOT displayed in message bubble', () => {
      const message = {
        id: '1',
        sender: 'ASSISTANT',
        content: 'Your parcel SDP-1001 is on the way.',
        toolName: 'track_parcel',
        // Internal data that must NOT be exposed:
        toolArguments: '{"trackingNumber":"SDP-1001"}',
        toolResult: '{"status":"IN_TRANSIT","events":[{"internal_id":"99"}]}',
      }

      render(<ChatMessageBubble message={message} />)

      expect(screen.getByText('Used tool: track_parcel')).toBeInTheDocument()
      expect(screen.queryByText(/toolArguments/i)).not.toBeInTheDocument()
      expect(screen.queryByText(/internal_id/i)).not.toBeInTheDocument()
    })

    it('3. Suggested questions appear when conversation is empty and can be clicked', async () => {
      const onSelect = vi.fn()
      render(<ChatMessageList messages={[]} loading={false} error={null} onSelectPrompt={onSelect} />)

      const promptBtn = screen.getByRole('button', { name: 'Where is my parcel?' })
      expect(promptBtn).toBeInTheDocument()

      await userEvent.click(promptBtn)
      expect(onSelect).toHaveBeenCalledWith('Where is my parcel?')
    })

    it('4. Loading indicator appears during assistant processing', () => {
      render(<ChatMessageList messages={[]} loading={true} error={null} onSelectPrompt={vi.fn()} />)
      expect(screen.getByRole('status', { name: /AI assistant is thinking/i })).toBeInTheDocument()
      expect(screen.getByText('AI Assistant is typing...')).toBeInTheDocument()
    })
  })

  describe('API and error handling', () => {
    it('1. HTTP 502 Bad Gateway displays AI temporarily unavailable notice', async () => {
      sessionStorage.setItem('swiftdrop_assistant_session_id', 'session-active')
      vi.spyOn(assistantApi, 'getSessionHistory').mockResolvedValue([])
      vi.spyOn(assistantApi, 'sendMessage').mockRejectedValue({
        status: 502,
        message: 'Upstream gateway error',
      })

      render(<ChatWidget />)
      await userEvent.click(screen.getByRole('button', { name: /Open SwiftDrop AI Delivery Assistant/i }))

      const textarea = await screen.findByRole('textbox', { name: /Type your message/i })
      await userEvent.type(textarea, 'Track parcel')
      await userEvent.click(screen.getByRole('button', { name: /Send message/i }))

      expect(
        await screen.findByText('The AI assistant is temporarily unavailable. Please try again shortly.')
      ).toBeInTheDocument()
    })

    it('2. Network failure (status 0) displays connection error notice', async () => {
      sessionStorage.setItem('swiftdrop_assistant_session_id', 'session-active')
      vi.spyOn(assistantApi, 'getSessionHistory').mockResolvedValue([])
      vi.spyOn(assistantApi, 'sendMessage').mockRejectedValue({
        status: 0,
        message: 'Network error',
      })

      render(<ChatWidget />)
      await userEvent.click(screen.getByRole('button', { name: /Open SwiftDrop AI Delivery Assistant/i }))

      const textarea = await screen.findByRole('textbox', { name: /Type your message/i })
      await userEvent.type(textarea, 'Track parcel')
      await userEvent.click(screen.getByRole('button', { name: /Send message/i }))

      expect(
        await screen.findByText('Cannot reach SwiftDrop right now. Check your connection and try again.')
      ).toBeInTheDocument()
    })

    it('3. HTTP 403 Forbidden displays access permission notice', async () => {
      sessionStorage.setItem('swiftdrop_assistant_session_id', 'session-active')
      vi.spyOn(assistantApi, 'getSessionHistory').mockResolvedValue([])
      vi.spyOn(assistantApi, 'sendMessage').mockRejectedValue({
        status: 403,
        message: 'Forbidden',
      })

      render(<ChatWidget />)
      await userEvent.click(screen.getByRole('button', { name: /Open SwiftDrop AI Delivery Assistant/i }))

      const textarea = await screen.findByRole('textbox', { name: /Type your message/i })
      await userEvent.type(textarea, 'Track parcel')
      await userEvent.click(screen.getByRole('button', { name: /Send message/i }))

      expect(
        await screen.findByText('You do not have permission to access this chat session.')
      ).toBeInTheDocument()
    })
  })
})
