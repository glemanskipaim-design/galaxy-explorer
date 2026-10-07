import { Link } from 'react-router'
import styles from './Pages.module.css'

export function NotFoundPage() {
  return (
    <div className={styles.notFound}>
      <title>Página não encontrada · Galaxy Explorer</title>
      <p className={styles.code} aria-hidden="true">
        404
      </p>
      <h1>Página não encontrada</h1>
      <p>Este endereço não existe por aqui.</p>
      <Link to="/">Voltar ao início</Link>
    </div>
  )
}
