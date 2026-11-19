import styles from './ChatWidget.module.css'

export default function ChatLoadingIndicator() {
  return (
    <div className={styles.loadingRow} role="status" aria-label="AI assistant is thinking">
      <div className={styles.loadingBubble}>
        <span className={styles.dot} />
        <span className={styles.dot} />
        <span className={styles.dot} />
      </div>
      <span className={styles.loadingText}>AI Assistant is typing...</span>
    </div>
  )
}
