import type { Measurement } from '../../api/types'
import { detectConflict } from '../../domain/conflict'
import type { MeasurementIndex } from '../../domain/displayGroups'
import { ConflictBadge } from './ConflictBadge'
import { MeasurementValue } from './MeasurementValue'
import styles from './Object.module.css'

interface AlternativeValuesProps {
  preferred: Measurement | null
  alternatives: Measurement[]
  propertyCode: string
  byId: MeasurementIndex
}

/** "Outros valores (N)"; selo de conflito no resumo quando divergem. Sem preferido: aberto. */
export function AlternativeValues({ preferred, alternatives, propertyCode, byId }: AlternativeValuesProps) {
  if (alternatives.length === 0) return null
  const conflict = detectConflict(preferred ? [preferred, ...alternatives] : alternatives)
  return (
    <details className={styles.alternatives} open={preferred === null}>
      <summary>
        <span>Outros valores publicados ({alternatives.length})</span>
        <ConflictBadge result={conflict} />
      </summary>
      <ul className={styles.alternativeList}>
        {alternatives.map((m) => (
          <li key={m.id}>
            <MeasurementValue measurement={m} propertyCode={propertyCode} byId={byId} />
          </li>
        ))}
      </ul>
    </details>
  )
}
