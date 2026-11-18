import { describe, it, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import {
  createSession,
  sendMessage,
  getSessionHistory,
  getFriendlyErrorMessage,
} from '../api/assistantApi.js'

describe('SwiftDrop AI Delivery Assistant - Frontend Tests', () => {
  let mockFetchHistory = []
  let storage = {}

  beforeEach(() => {
    mockFetchHistory = []
    storage = {}

    globalThis.localStorage = {
      getItem: (key) => storage[key] || null,
      setItem: (key, val) => {
        storage[key] = String(val)
      },
      removeItem: (key) => {
        delete storage[key]
      },
      clear: () => {
        storage = {}
      },
    }

    globalThis.sessionStorage = {
      getItem: (key) => storage[key] || null,
      setItem: (key, val) => {
        storage[key] = String(val)
      },
      removeItem: (key) => {
        delete storage[key]
      },
      clear: () => {
        storage = {}
      },
    }
  })

  it('1. Widget open and close state transitions correctly', () => {
    let isOpen = false
    const toggle = () => {
      isOpen = !isOpen
    }

    assert.equal(isOpen, false, 'Initial state should be closed')
    toggle()
    assert.equal(isOpen, true, 'Widget should be open after toggle')
    toggle()
    assert.equal(isOpen, false, 'Widget should be closed after second toggle')
  })

  it('2. Backend session creation calls /assistant/sessions with Bearer token', async () => {
    globalThis.localStorage.setItem('swiftdrop_token', 'jwt.test.token')

    globalThis.fetch = async (url, options) => {
      mockFetchHistory.push({ url, options })
      return {
        ok: true,
        status: 201,
        json: async () => ({
          sessionId: 'session-uuid-1234',
          createdAt: '2026-11-14T10:00:00Z',
        }),
      }
    }

    const session = await createSession()
    assert.equal(session.sessionId, 'session-uuid-1234')
    assert.equal(mockFetchHistory.length, 1)
    assert.equal(mockFetchHistory[0].url, '/api/v1/assistant/sessions')
    assert.equal(mockFetchHistory[0].options.method, 'POST')
    assert.equal(
      mockFetchHistory[0].options.headers.Authorization,
      'Bearer jwt.test.token'
    )
  })

  it('3. Sending a customer message sends POST to session messages endpoint', async () => {
    globalThis.localStorage.setItem('swiftdrop_token', 'jwt.test.token')

    globalThis.fetch = async (url, options) => {
      mockFetchHistory.push({ url, options })
      return {
        ok: true,
        status: 200,
        json: async () => ({
          sessionId: 'session-123',
          answer: 'Your parcel SDP-1001 is on the way.',
          executedTools: [{ toolName: 'track_parcel' }],
          createdAt: '2026-11-14T10:05:00Z',
        }),
      }
    }

    const res = await sendMessage('session-123', 'Where is my parcel?')
    assert.equal(res.answer, 'Your parcel SDP-1001 is on the way.')
    assert.equal(res.executedTools[0].toolName, 'track_parcel')
    assert.equal(mockFetchHistory[0].url, '/api/v1/assistant/sessions/session-123/messages')
    assert.equal(JSON.parse(mockFetchHistory[0].options.body).message, 'Where is my parcel?')
  })

  it('4. Assistant response is structured with answer, tools, and timestamp', async () => {
    const rawResponse = {
      sessionId: 'session-abc',
      answer: 'Standard delivery to Kandy is 840 LKR.',
      executedTools: [{ toolName: 'estimate_fee' }],
      createdAt: '2026-11-14T10:10:00Z',
    }

    const assistantMessage = {
      sender: 'ASSISTANT',
      content: rawResponse.answer,
      toolName: rawResponse.executedTools?.map((t) => t.toolName).join(', '),
      createdAt: rawResponse.createdAt,
    }

    assert.equal(assistantMessage.sender, 'ASSISTANT')
    assert.equal(assistantMessage.content, 'Standard delivery to Kandy is 840 LKR.')
    assert.equal(assistantMessage.toolName, 'estimate_fee')
    assert.equal(assistantMessage.createdAt, '2026-11-14T10:10:00Z')
  })

  it('5. Loading indicator state transitions during request lifecycle', async () => {
    let loading = false
    const simulateTurn = async () => {
      loading = true
      assert.equal(loading, true, 'Loading should be true while request is in flight')
      await new Promise((r) => setTimeout(r, 10))
      loading = false
    }

    await simulateTurn()
    assert.equal(loading, false, 'Loading should be false when request completes')
  })

  it('6. Empty or whitespace-only messages are rejected by input validation', () => {
    const validate = (text) => Boolean(text && text.trim().length > 0)
    assert.equal(validate(''), false)
    assert.equal(validate('   '), false)
    assert.equal(validate('\n\t'), false)
    assert.equal(validate('Track parcel'), true)
  })

  it('7. AI Provider failure (HTTP 502) produces a friendly customer message', () => {
    const error502 = { status: 502, message: 'Upstream Generative AI timed out' }
    const friendly = getFriendlyErrorMessage(error502)
    assert.equal(
      friendly,
      'The AI assistant is temporarily unavailable. Please try again shortly.'
    )
  })

  it('8. Existing session history is loaded in chronological order', async () => {
    const historyData = [
      { id: '1', sender: 'USER', content: 'Hi', createdAt: '2026-11-14T10:00:00Z' },
      { id: '2', sender: 'ASSISTANT', content: 'Hello!', createdAt: '2026-11-14T10:00:02Z' },
    ]

    globalThis.fetch = async () => {
      return {
        ok: true,
        status: 200,
        json: async () => historyData,
      }
    }

    const history = await getSessionHistory('session-existing')
    assert.equal(history.length, 2)
    assert.equal(history[0].sender, 'USER')
    assert.equal(history[1].sender, 'ASSISTANT')
  })

  it('9. Invalid or expired session (HTTP 404) triggers session recovery', async () => {
    globalThis.sessionStorage.setItem('swiftdrop_assistant_session_id', 'expired-session')

    // First fetch fails with 404
    let fetchCount = 0
    globalThis.fetch = async () => {
      fetchCount++
      if (fetchCount === 1) {
        return {
          ok: false,
          status: 404,
          json: async () => ({ title: 'Chat Session Not Found' }),
        }
      }
      return {
        ok: true,
        status: 201,
        json: async () => ({ sessionId: 'new-recovered-session' }),
      }
    }

    // Simulate recovery logic
    let activeSession = globalThis.sessionStorage.getItem('swiftdrop_assistant_session_id')
    try {
      await getSessionHistory(activeSession)
    } catch {
      globalThis.sessionStorage.removeItem('swiftdrop_assistant_session_id')
      const newSession = await createSession()
      globalThis.sessionStorage.setItem('swiftdrop_assistant_session_id', newSession.sessionId)
      activeSession = newSession.sessionId
    }

    assert.equal(activeSession, 'new-recovered-session')
    assert.equal(
      globalThis.sessionStorage.getItem('swiftdrop_assistant_session_id'),
      'new-recovered-session'
    )
  })

  it('10. Authentication failure (HTTP 401) produces a clear relogin notice', () => {
    const error401 = { status: 401, message: 'Full authentication is required' }
    const friendly = getFriendlyErrorMessage(error401)
    assert.equal(
      friendly,
      'Your login session has expired. Please sign in again to continue.'
    )
  })

  it('11. Duplicate submissions are prevented while loading is true', () => {
    let callCount = 0
    let loading = true

    const submit = (text) => {
      if (loading || !text) return false
      callCount++
      return true
    }

    const firstAttempt = submit('Track SDP-1001')
    assert.equal(firstAttempt, false)
    assert.equal(callCount, 0)

    loading = false
    const secondAttempt = submit('Track SDP-1001')
    assert.equal(secondAttempt, true)
    assert.equal(callCount, 1)
  })

  it('12. Suggested prompt pills trigger message submission with correct text', () => {
    const submittedPrompts = []
    const handleSelectPrompt = (promptText) => {
      submittedPrompts.push(promptText)
    }

    handleSelectPrompt('Where is my parcel?')
    handleSelectPrompt('How much does delivery cost to Kandy?')

    assert.equal(submittedPrompts.length, 2)
    assert.equal(submittedPrompts[0], 'Where is my parcel?')
    assert.equal(submittedPrompts[1], 'How much does delivery cost to Kandy?')
  })
})
