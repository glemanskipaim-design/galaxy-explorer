import { cx } from '../../components/ui/cx'
import { epistemicInfo, type EpistemicVariant } from '../../domain/epistemic'
import styles from './Object.module.css'

/** Icone por natureza: forma diferente para cada uma (nunca so cor). */
const ICON: Record<EpistemicVariant, string> = {
  observed: '◉',
  derived: '◈',
  computed: 'ƒ',
  estimated: '≈',
  model: '⌬',
  unknown: '?',
}

/** Natureza epistemica do valor: rotulo + icone + cor, com descricao curta. */
export function EpistemicBadge({ epistemic }: { epistemic: string }) {
  const info = epistemicInfo(epistemic)
  return (
    <span className={cx(styles.badge, styles[`epi_${info.variant}`])} title={info.description}>
      <span aria-hidden="true" className={styles.badgeIcon}>
        {ICON[info.variant]}
      </span>
      {info.label}
      {info.variant === 'unknown' && <span className={styles.badgeNote}> (natureza não catalogada)</span>}
    </span>
  )
}
