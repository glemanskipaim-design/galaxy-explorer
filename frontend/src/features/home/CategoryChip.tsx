import { Link } from 'react-router'
import { cx } from '../../components/ui/cx'
import { formatNumber } from '../../domain/format/number'
import styles from './Home.module.css'

interface CategoryChipProps {
  label: string
  to: string | null
  count: number | null
  disabledReason: string | null
}

/** Ativo -> link com contagem real. Desabilitado -> nao interativo, motivo VISIVEL (nao so cor). */
export function CategoryChip({ label, to, count, disabledReason }: CategoryChipProps) {
  if (to === null) {
    return (
      <span className={cx(styles.chip, styles.chipDisabled)} aria-disabled="true">
        <span className={styles.chipLabel}>{label}</span>
        <span className={styles.chipNote}>{disabledReason}</span>
      </span>
    )
  }
  return (
    <Link className={styles.chip} to={to}>
      <span className={styles.chipLabel}>{label}</span>
      {count !== null && (
        <span className={styles.chipCount}>
          {formatNumber(count, { fractionDigits: 0 })}
          <span className={styles.chipCountUnit}> {count === 1 ? 'objeto' : 'objetos'}</span>
        </span>
      )}
    </Link>
  )
}
