import { useEffect, useRef } from 'react'
import { Sparkles, AlertCircle } from 'lucide-react'
import ChatMessageBubble from './ChatMessageBubble'
import ChatLoadingIndicator from './ChatLoadingIndicator'
import styles from './ChatWidget.module.css'

const SUGGESTED_PROMPTS = [
  'Where is my parcel?',
  'How much does delivery cost to Kandy?',
  'Show my parcels',
]

export default function ChatMessageList({ messages, loading, error, onSelectPrompt }) {
  const scrollRef = useRef(null)

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [messages, loading, error])

  return (
    <div className={styles.messageList} ref={scrollRef}>
      <div className={styles.welcomeBanner}>
        <div className={styles.welcomeIcon} aria-hidden="true">
          <Sparkles size={20} />
        </div>
        <div>
          <h4 className={styles.welcomeTitle}>SwiftDrop AI Assistant</h4>
          <p className={styles.welcomeSubtitle}>
            Ask about your shipments, track parcels, or calculate delivery rates in real-time.
          </p>
        </div>
      </div>

      {messages.map((msg, index) => (
        <ChatMessageBubble key={msg.id || `msg-${index}`} message={msg} />
      ))}

      {loading && <ChatLoadingIndicator />}

      {error && (
        <div className={styles.errorAlert} role="alert">
          <AlertCircle size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      {messages.length === 0 && !loading && (
        <div className={styles.suggestedContainer}>
          <span className={styles.suggestedHeader}>Suggested queries:</span>
          <div className={styles.suggestedGrid}>
            {SUGGESTED_PROMPTS.map((prompt) => (
              <button
                key={prompt}
                type="button"
                className={styles.promptButton}
                onClick={() => onSelectPrompt(prompt)}
              >
                {prompt}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
