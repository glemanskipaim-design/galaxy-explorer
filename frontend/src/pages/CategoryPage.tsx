import { Link, useParams, useSearchParams } from 'react-router'
import { ApiRequestError } from '../api/http'
import { useCategoryObjects } from '../api/queries'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { CATEGORY_MAX_OFFSET, CATEGORY_PAGE_SIZE } from '../config'
import { maxRequestablePage, pageCountOf, parsePage } from '../domain/categoryPaging'
import { formatNumber } from '../domain/format/number'
import styles from '../features/category/Category.module.css'
import { ObjectList } from '../features/category/ObjectList'
import { Pagination } from '../features/category/Pagination'
import pageStyles from './Pages.module.css'

const MAX_PAGE = maxRequestablePage(CATEGORY_PAGE_SIZE, CATEGORY_MAX_OFFSET)

function pageHref(code: string, page: number): string {
  const base = `/categories/${encodeURIComponent(code)}`
  return page <= 1 ? base : `${base}?page=${page}`
}

/** Pagina fora da faixa: estado explicito, nunca "pagina 50 de 2" nem o 400 generico do backend. */
function PageOutOfRange({ code, page, pageCount }: { code: string; page: number; pageCount: number | null }) {
  return (
    <div className={pageStyles.notFound}>
      <title>Página inexistente · Galaxy Explorer</title>
      <h1>Página {formatNumber(page, { fractionDigits: 0 })} não existe</h1>
      {pageCount === null ? (
        <>
          <p>Esta listagem não chega a essa página.</p>
          <Link to={pageHref(code, 1)}>Ir para a primeira página</Link>
        </>
      ) : (
        <>
          <p>
            Esta categoria tem {pageCount} {pageCount === 1 ? 'página' : 'páginas'}.
          </p>
          <Link to={pageHref(code, pageCount)}>Ir para a última página</Link>
        </>
      )}
    </div>
  )
}

export function CategoryPage() {
  const { code = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const page = parsePage(params.get('page'))
  const requestable = page <= MAX_PAGE
  const list = useCategoryObjects(code, page, requestable)

  if (!requestable) return <PageOutOfRange code={code} page={page} pageCount={null} />
  if (list.isPending) return <LoadingState label="Carregando a categoria…" />
  if (list.isError) {
    if (list.error instanceof ApiRequestError && list.error.status === 404) {
      return (
        <div className={pageStyles.notFound}>
          <title>Categoria inexistente · Galaxy Explorer</title>
          <h1>Categoria inexistente</h1>
          <p>Não há categoria “{code}” no catálogo.</p>
          <Link to="/">Voltar ao início</Link>
        </div>
      )
    }
    return <ErrorState error={list.error} onRetry={() => void list.refetch()} />
  }

  const { category, total, items } = list.data
  const pageCount = pageCountOf(total, CATEGORY_PAGE_SIZE)
  if (page > pageCount) return <PageOutOfRange code={code} page={page} pageCount={pageCount} />

  const goTo = (n: number) => {
    const next = new URLSearchParams(params)
    if (n <= 1) next.delete('page')
    else next.set('page', String(n))
    setParams(next)
    window.scrollTo({ top: 0 })
  }

  return (
    <div>
      <title>{`${category.namePt} · Galaxy Explorer`}</title>
      <header className={styles.head}>
        <p className={styles.eyebrow}>Categoria</p>
        <h1 className={styles.title}>{category.namePt}</h1>
        <p className={styles.count}>
          {formatNumber(total, { fractionDigits: 0 })} {total === 1 ? 'objeto' : 'objetos'} no catálogo local
        </p>
      </header>
      {items.length === 0 ? (
        <p className={styles.empty}>Nenhum objeto nesta categoria por enquanto.</p>
      ) : (
        <ObjectList items={items} />
      )}
      <Pagination page={page} pageCount={pageCount} onPage={goTo} />
    </div>
  )
}
