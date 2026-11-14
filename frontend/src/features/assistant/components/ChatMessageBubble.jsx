import { Bot, Wrench } from 'lucide-react'
import styles from './ChatWidget.module.css'

function formatTime(isoString) {
  if (!isoString) return ''
  try {
    const date = new Date(isoString)
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

export default function ChatMessageBubble({ message }) {
  const isUser = message.sender === 'USER'

  return (
    <div className={`${styles.messageRow} ${isUser ? styles.userRow : styles.assistantRow}`}>
      {!isUser && (
        <div className={styles.avatar} aria-hidden="true">
          <Bot size={16} />
        </div>
      )}
      <div className={styles.messageContent}>
        {message.toolName && (
          <div className={styles.toolBadge} title={`Tool used: ${message.toolName}`}>
            <Wrench size={12} aria-hidden="true" />
            <span>Used tool: {message.toolName}</span>
          </div>
        )}
        <div className={`${styles.bubble} ${isUser ? styles.userBubble : styles.assistantBubble}`}>
          <p className={styles.bubbleText}>{message.content}</p>
        </div>
        {message.createdAt && (
          <span className={styles.timestamp}>{formatTime(message.createdAt)}</span>
        )}
      </div>
    </div>
  )
}
