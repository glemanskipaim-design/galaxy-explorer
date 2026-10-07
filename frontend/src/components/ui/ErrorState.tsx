import styles from './ErrorState.module.css'
import { errorMessage } from './errorMessage'

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className={styles.error} role="alert">
      <p>{errorMessage(error)}</p>
      {onRetry && (
        <button type="button" className={styles.retry} onClick={onRetry}>
          Tentar de novo
        </button>
      )}
    </div>
  )
}
