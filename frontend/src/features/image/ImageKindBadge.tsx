import type { ImageKind } from '../../api/types'
import { cx } from '../../components/ui/cx'
import { imageKindInfo } from '../../domain/imageKind'
import styles from './Image.module.css'

const ICON: Record<string, string> = { photo: '◉', survey: '▦', warning: '✎', unknown: '?' }

/** Selo do tipo de imagem: texto + icone (nunca so cor). */
export function ImageKindBadge({ kind, compact = false }: { kind: ImageKind; compact?: boolean }) {
  const info = imageKindInfo(kind)
  return (
    <span className={cx(styles.badge, styles[`badge_${info.variant}`], compact && styles.badgeCompact)}
      title={info.description}>
      <span aria-hidden="true">{ICON[info.variant]}</span> {info.label}
    </span>
  )
}
