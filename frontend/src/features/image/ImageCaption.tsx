import type { ObjectImage } from '../../api/types'
import { ExternalLink } from '../../components/ui/ExternalLink'
import { creditHasOwnLabel } from '../../domain/imageCredit'
import { ImageKindBadge } from './ImageKindBadge'
import styles from './Image.module.css'

/**
 * Legenda de uma imagem: credito LITERAL completo (nunca truncado) + agradecimento da fonte, selo do
 * tipo, licenca com link, provedor/missao e link para a pagina original. Links externos so via
 * ExternalLink (safeUrl: so https; o resto vira texto).
 */
export function ImageCaption({ image }: { image: ObjectImage }) {
  const isCutout = image.kind === 'survey_cutout'
  return (
    <figcaption className={styles.caption}>
      <ImageKindBadge kind={image.kind} />
      {image.title !== null && <span className={styles.captionTitle}>{image.title}</span>}
      <p className={styles.credit}>
        {!creditHasOwnLabel(image.credit) && <span className={styles.metaLabel}>Crédito: </span>}
        {image.credit}
      </p>
      {image.acknowledgment !== null && <p className={styles.ack}>{image.acknowledgment}</p>}
      <ul className={styles.meta}>
        <li>
          <span className={styles.metaLabel}>Licença: </span>
          <ExternalLink href={image.license.url}>{image.license.name}</ExternalLink>
        </li>
        <li>
          <span className={styles.metaLabel}>Provedor: </span>
          {image.provider}
        </li>
        {(image.mission !== null || image.instrument !== null) && (
          <li>
            <span className={styles.metaLabel}>Missão/instrumento: </span>
            {[image.mission, image.instrument].filter((v) => v !== null).join(' · ')}
          </li>
        )}
        <li>
          <ExternalLink href={image.pageUrl}>{isCutout ? 'Refazer este recorte no CDS' : 'Página original'}</ExternalLink>
        </li>
      </ul>
    </figcaption>
  )
}
