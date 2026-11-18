import { useState, useCallback, useRef } from 'react'
import { Bot, X, RefreshCw } from 'lucide-react'
import { createSession, sendMessage, getSessionHistory, getFriendlyErrorMessage } from '../api/assistantApi'
import ChatMessageList from './ChatMessageList'
import ChatInput from './ChatInput'
import styles from './ChatWidget.module.css'

const STORAGE_SESSION_KEY = 'swiftdrop_assistant_session_id'

export default function ChatWidget() {
  const [isOpen, setIsOpen] = useState(false)
  const [sessionId, setSessionId] = useState(null)
  const [messages, setMessages] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const isInitializingRef = useRef(false)

  // Initialize or restore session when widget opens
  const initSession = useCallback(async () => {
    if (isInitializingRef.current) return
    isInitializingRef.current = true
    setError(null)

    const storedSessionId = sessionStorage.getItem(STORAGE_SESSION_KEY)

    if (storedSessionId) {
      try {
        setLoading(true)
        const history = await getSessionHistory(storedSessionId)
        setSessionId(storedSessionId)
        setMessages(history || [])
        setLoading(false)
        isInitializingRef.current = false
        return storedSessionId
      } catch {
        // Session invalid, expired, or access denied - clear and create a fresh session
        sessionStorage.removeItem(STORAGE_SESSION_KEY)
      }
    }

    try {
      setLoading(true)
      const newSession = await createSession()
      sessionStorage.setItem(STORAGE_SESSION_KEY, newSession.sessionId)
      setSessionId(newSession.sessionId)
      setMessages([])
      setLoading(false)
      isInitializingRef.current = false
      return newSession.sessionId
    } catch (err) {
      setError(getFriendlyErrorMessage(err))
      setLoading(false)
      isInitializingRef.current = false
      return null
    }
  }, [])

  const handleToggle = () => {
    const nextOpen = !isOpen
    setIsOpen(nextOpen)
    setError(null)
    if (nextOpen && !sessionId) {
      initSession()
    }
  }

  const handleSendMessage = async (text) => {
    if (!text || loading) return

    let activeSessionId = sessionId
    if (!activeSessionId) {
      activeSessionId = await initSession()
      if (!activeSessionId) return
    }

    setError(null)

    // Optimistic customer message
    const userMessage = {
      id: `temp-${Date.now()}`,
      sender: 'USER',
      content: text,
      createdAt: new Date().toISOString(),
    }
    setMessages((prev) => [...prev, userMessage])
    setLoading(true)

    try {
      const response = await sendMessage(activeSessionId, text)
      const assistantMessage = {
        id: `turn-${Date.now()}`,
        sender: 'ASSISTANT',
        content: response.answer,
        toolName: response.executedTools?.map((t) => t.toolName).filter(Boolean).join(', ') || null,
        createdAt: response.createdAt || new Date().toISOString(),
      }
      setMessages((prev) => [...prev, assistantMessage])
    } catch (err) {
      // If 404, session expired on backend - clear session to recover on next turn
      if (err.status === 404) {
        sessionStorage.removeItem(STORAGE_SESSION_KEY)
        setSessionId(null)
      }
      setError(getFriendlyErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const handleNewSession = async () => {
    sessionStorage.removeItem(STORAGE_SESSION_KEY)
    setSessionId(null)
    setMessages([])
    setError(null)
    await initSession()
  }

  return (
    <>
      {/* Floating launcher button */}
      {!isOpen && (
        <button
          type="button"
          className={styles.floatingLauncher}
          onClick={handleToggle}
          aria-expanded={isOpen}
          aria-label="Open SwiftDrop AI Delivery Assistant"
        >
          <div className={styles.launcherIconWrapper}>
            <Bot size={18} aria-hidden="true" />
          </div>
          <span>AI Assistant</span>
        </button>
      )}

      {/* Chat Window Panel */}
      {isOpen && (
        <section
          className={styles.chatPanel}
          role="dialog"
          aria-label="SwiftDrop AI Assistant Chat"
        >
          {/* Header */}
          <header className={styles.chatHeader}>
            <div className={styles.headerInfo}>
              <div className={styles.headerIcon} aria-hidden="true">
                <Bot size={20} />
              </div>
              <div>
                <h3 className={styles.headerTitle}>SwiftDrop Assistant</h3>
                <div className={styles.headerSubtitle}>
                  <span className={styles.statusIndicator} />
                  <span>Online & ready</span>
                </div>
              </div>
            </div>
            <div className={styles.headerActions}>
              <button
                type="button"
                className={styles.closeButton}
                onClick={handleNewSession}
                title="Start a new chat session"
                aria-label="New session"
              >
                <RefreshCw size={16} />
              </button>
              <button
                type="button"
                className={styles.closeButton}
                onClick={handleToggle}
                title="Close chat window"
                aria-label="Close chat"
              >
                <X size={18} />
              </button>
            </div>
          </header>

          {/* Messages */}
          <ChatMessageList
            messages={messages}
            loading={loading}
            error={error}
            onSelectPrompt={handleSendMessage}
          />

          {/* Input */}
          <ChatInput onSendMessage={handleSendMessage} disabled={loading} />
        </section>
      )}
    </>
  )
}
