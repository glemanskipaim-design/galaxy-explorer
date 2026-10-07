import type { ConflictResult } from '../../domain/conflict'
import styles from './Object.module.css'

const RULE_TEXT: Record<ConflictResult['rule'], string> = {
  '3sigma': 'diferença maior que 3σ combinados',
  '10pct': 'diferença maior que 10%, sem incerteza publicada',
  insufficient: '',
}

/** "Fontes divergem" + a regra que disparou (Fase 0 §7). Nada se nao diverge. */
export function ConflictBadge({ result }: { result: ConflictResult }) {
  if (!result.diverges) return null
  return (
    <span className={styles.conflict}>
      <span aria-hidden="true">⚠</span> Fontes divergem
      <span className={styles.conflictRule}> ({RULE_TEXT[result.rule]})</span>
    </span>
  )
}
