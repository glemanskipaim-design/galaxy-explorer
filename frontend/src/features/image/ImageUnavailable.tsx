import type { ImageAbsence } from '../../api/types'
import styles from './Image.module.css'

/**
 * "Imagem não disponível" explicito, com o motivo que a API deu. O desenho e abstrato (aneis) e
 * decorativo: nada aqui pode parecer o objeto. Mesmo espaco reservado (4:3) que um hero ocuparia.
 */
export function ImageUnavailable({ absence }: { absence: ImageAbsence | null }) {
  return (
    <figure className={styles.unavailable}>
      <div className={styles.rings} aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <figcaption>
        <strong>Imagem não disponível</strong>
        <span>{absence?.message ?? 'A API não informou o motivo.'}</span>
      </figcaption>
    </figure>
  )
}
