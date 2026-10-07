import styles from './Object.module.css'

/**
 * Sem foto decorativa que pareca ser do objeto: imagens so com credito e licenca, em bloco
 * futuro. O desenho e abstrato (aneis) e marcado como decorativo.
 */
export function ImagePlaceholder() {
  return (
    <figure className={styles.imagePlaceholder}>
      <div className={styles.rings} aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <figcaption>
        <strong>Imagem não disponível</strong>
        <span>
          Imagens reais, com crédito e licença de uso, chegam em uma próxima etapa. Nada aqui é
          ilustração do objeto.
        </span>
      </figcaption>
    </figure>
  )
}
