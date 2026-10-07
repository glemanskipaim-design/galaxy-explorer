import styles from './LoadingState.module.css'

/** Carregamento anunciado ao leitor de tela, com texto visivel (nao so animacao). */
export function LoadingState({ label }: { label: string }) {
  return (
    <div className={styles.loading} role="status" aria-live="polite">
      <span className={styles.orbit} aria-hidden="true">
        <span className={styles.dot} />
      </span>
      <span>{label}</span>
    </div>
  )
}
