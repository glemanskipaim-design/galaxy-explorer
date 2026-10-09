import type { ImageThumbnail } from '../../api/types'
import { ProgressiveImage } from '../../components/ui/ProgressiveImage'
import { ImageKindBadge } from './ImageKindBadge'
import styles from './Image.module.css'

/**
 * Miniatura do hero em busca/listas: imagem + selo do tipo + nome CURTO do provedor (decisao do
 * usuario; o credito literal completo fica na pagina do objeto) + codigo da licenca. Sem `<a>`
 * interno: o item inteiro ja e um link. A imagem e decorativa (alt vazio) porque o nome do objeto
 * ja esta no texto do link.
 */
export function ResultThumbnail({ thumbnail }: { thumbnail: ImageThumbnail | null }) {
  if (thumbnail === null) {
    return (
      <span className={styles.thumb}>
        <span className={styles.thumbEmpty}>sem imagem</span>
        <span />
      </span>
    )
  }
  return (
    <span className={styles.thumb}>
      <span className={styles.thumbFrame}>
        <ProgressiveImage src={thumbnail.url} width={thumbnail.width} height={thumbnail.height} alt="" loading="lazy" />
      </span>
      <span className={styles.thumbText}>
        <ImageKindBadge kind={thumbnail.kind} compact />
        <span>
          Imagem: {thumbnail.provider} · {thumbnail.licenseCode}
        </span>
      </span>
    </span>
  )
}
