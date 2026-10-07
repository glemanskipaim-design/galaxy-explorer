import styles from './Category.module.css'

interface PaginationProps {
  page: number
  pageCount: number
  onPage: (n: number) => void
}

export function Pagination({ page, pageCount, onPage }: PaginationProps) {
  if (pageCount <= 1) return null
  return (
    <nav className={styles.pagination} aria-label="Paginação">
      <button type="button" className={styles.pageButton} onClick={() => onPage(page - 1)} disabled={page <= 1}>
        <span aria-hidden="true">←</span> Anterior
      </button>
      <span className={styles.pageInfo} aria-current="page">
        página {page} de {pageCount}
      </span>
      <button
        type="button"
        className={styles.pageButton}
        onClick={() => onPage(page + 1)}
        disabled={page >= pageCount}
      >
        Próxima <span aria-hidden="true">→</span>
      </button>
    </nav>
  )
}
