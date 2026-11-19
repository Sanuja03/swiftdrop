import { useState, useRef, useEffect } from 'react'
import { Send } from 'lucide-react'
import styles from './ChatWidget.module.css'

const MAX_CHAR_COUNT = 1000

export default function ChatInput({ onSendMessage, disabled }) {
  const [text, setText] = useState('')
  const textareaRef = useRef(null)

  useEffect(() => {
    if (!disabled && textareaRef.current) {
      textareaRef.current.focus()
    }
  }, [disabled])

  const handleSubmit = (e) => {
    if (e) e.preventDefault()
    const trimmed = text.trim()
    if (!trimmed || disabled) return

    onSendMessage(trimmed)
    setText('')
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit()
    }
  }

  return (
    <form className={styles.inputForm} onSubmit={handleSubmit}>
      <div className={styles.inputWrapper}>
        <textarea
          ref={textareaRef}
          className={styles.textarea}
          value={text}
          onChange={(e) => setText(e.target.value.slice(0, MAX_CHAR_COUNT))}
          onKeyDown={handleKeyDown}
          placeholder="Ask a question or type a parcel tracking number..."
          aria-label="Type your message"
          disabled={disabled}
          rows={1}
          maxLength={MAX_CHAR_COUNT}
        />
        <button
          type="submit"
          className={styles.sendButton}
          disabled={disabled || !text.trim()}
          aria-label="Send message"
        >
          <Send size={18} aria-hidden="true" />
        </button>
      </div>
      {text.length > 800 && (
        <div className={styles.charCounter}>
          {text.length}/{MAX_CHAR_COUNT}
        </div>
      )}
    </form>
  )
}
