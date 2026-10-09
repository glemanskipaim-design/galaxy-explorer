import type { ObjectImage } from '../../api/types'
import { ProgressiveImage } from '../../components/ui/ProgressiveImage'
import { imageAlt } from '../../domain/imageAlt'
import { ImageCaption } from './ImageCaption'
import styles from './Image.module.css'

/**
 * Imagem hero (a primeira da API, que ja vem ordenada: hero primeiro, depois gallery_rank) com
 * legenda completa; as demais numa lista simples (miniatura + credito completo), sem lightbox.
 */
export function ObjectImages({ images, displayName }: { images: ObjectImage[]; displayName: string }) {
  const [hero, ...extras] = images
  if (hero === undefined) return null
  return (
    <section aria-label="Imagens">
      <figure className={styles.figure}>
        <ProgressiveImage
          src={hero.url}
          width={hero.width}
          height={hero.height}
          alt={imageAlt(displayName, hero.kind, hero.provider)}
          placeholderSrc={hero.thumbnail.url}
          loading="eager"
          fetchPriority="high"
        />
        <ImageCaption image={hero} />
      </figure>
      {extras.length > 0 && (
        <div className={styles.extras}>
          <h2 className={styles.extrasTitle}>Outras imagens ({extras.length})</h2>
          <ul className={styles.extraList}>
            {extras.map((img) => (
              <li key={img.id}>
                <figure className={styles.extra}>
                  <ProgressiveImage
                    src={img.thumbnail.url}
                    width={img.thumbnail.width}
                    height={img.thumbnail.height}
                    alt={imageAlt(displayName, img.kind, img.provider)}
                    loading="lazy"
                  />
                  <ImageCaption image={img} />
                </figure>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}
