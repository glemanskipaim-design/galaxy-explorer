import { useState } from 'react'
import { safeImageSrc } from '../../domain/imageSrc'
import { cx } from './cx'
import styles from './ProgressiveImage.module.css'

interface Props {
  src: string
  width: number
  height: number
  alt: string
  /** miniatura ja carregada, mostrada desfocada por baixo ate a imagem chegar */
  placeholderSrc?: string
  loading: 'eager' | 'lazy'
  fetchPriority?: 'high' | 'low' | 'auto'
}

/**
 * Imagem sem salto de layout: o wrapper reserva `aspect-ratio: width / height` e o `<img>` tem
 * width/height. `src` so passa se for arquivo da propria API ({@link safeImageSrc}); falha vira
 * aviso textual (o credito, fora daqui, continua visivel). Wrapper e `<span>` (nao `<div>`) para
 * poder ficar dentro de um link sem gerar HTML invalido.
 */
export function ProgressiveImage(props: Props) {
  // key={src}: trocar de imagem remonta o corpo e zera loaded/failed (nada de "falha" herdada de outra imagem)
  return <ProgressiveImageBody key={props.src} {...props} />
}

function ProgressiveImageBody({ src, width, height, alt, placeholderSrc, loading, fetchPriority }: Props) {
  const [loaded, setLoaded] = useState(false)
  const [failed, setFailed] = useState(false)
  const safe = safeImageSrc(src)
  const placeholder = safeImageSrc(placeholderSrc)
  return (
    <span className={styles.frame} style={{ aspectRatio: `${width} / ${height}` }}>
      {safe === null || failed ? (
        <span className={styles.failed} role={alt === '' ? undefined : 'img'} aria-label={alt === '' ? undefined : alt}>
          Falha ao carregar a imagem
        </span>
      ) : (
        <>
          {placeholder !== null && !loaded && (
            <img className={styles.placeholder} src={placeholder} alt="" aria-hidden="true" width={width} height={height} />
          )}
          <img
            className={cx(styles.image, loaded && styles.loaded)}
            src={safe}
            alt={alt}
            width={width}
            height={height}
            loading={loading}
            decoding="async"
            fetchPriority={fetchPriority}
            onLoad={() => setLoaded(true)}
            onError={() => setFailed(true)}
          />
        </>
      )}
    </span>
  )
}
