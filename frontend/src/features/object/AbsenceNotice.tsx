import type { Absence } from '../../api/types'
import { formatNumber } from '../../domain/format/number'
import styles from './Object.module.css'

/**
 * Ausencia NAO e erro: tom neutro. Texto principal da API (ou o padrao para NOT_PROVIDED_BY_SOURCE).
 * Sem motivo da API (`absence === null`) a UI nao inventa um: diz que o motivo nao foi informado.
 */
export function AbsenceNotice({ absence }: { absence: Absence | null }) {
  const text =
    absence === null
      ? 'Indisponível (a API não informou o motivo)'
      : absence.code === 'NOT_PROVIDED_BY_SOURCE'
        ? 'Não disponível na fonte'
        : absence.message
  return (
    <p className={styles.absence}>
      <span aria-hidden="true" className={styles.absenceIcon}>
        ∅
      </span>
      <span>{text}</span>
      {absence?.snr != null && (
        <span className={styles.absenceSnr}>S/N da paralaxe = {formatNumber(absence.snr, { significantDigits: 2 })}</span>
      )}
    </p>
  )
}
