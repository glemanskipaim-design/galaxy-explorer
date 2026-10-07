import styles from './SkipLink.module.css'

/** Primeiro foco da pagina: pula o cabecalho. */
export function SkipLink() {
  return (
    <a className={styles.skip} href="#conteudo">
      Pular para o conteúdo
    </a>
  )
}
