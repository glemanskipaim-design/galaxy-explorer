import { useSearch } from '../../api/queries'
import { ErrorState } from '../../components/ui/ErrorState'
import { LoadingState } from '../../components/ui/LoadingState'
import { TIMINGS } from '../../config'
import { ImportOffer } from './ImportOffer'
import styles from './Search.module.css'
import { SearchResultItem } from './SearchResultItem'
import { useDebouncedValue } from './useDebouncedValue'

interface SearchResultsProps {
  query: string
  /** ha uma importacao em andamento na URL: nao oferecer outra */
  importInProgress: boolean
  onImportStarted: (jobId: number) => void
}

function countLabel(n: number): string {
  if (n === 0) return 'Nenhum resultado no catálogo local'
  return n === 1 ? '1 resultado' : `${n} resultados`
}

/**
 * Estados: ocioso (termo vazio) / carregando / erro / com resultados / vazio.
 * Oferece importacao quando NAO ha hit EXACT — lista vazia ou so aproximados (risco R7: coerente
 * com o worker do backend, que so considera EXACT como "ja existe").
 */
export function SearchResults({ query, importInProgress, onImportStarted }: SearchResultsProps) {
  const debounced = useDebouncedValue(query.trim(), TIMINGS.searchDebounceMs)
  const search = useSearch(debounced)

  if (debounced === '') return null
  if (search.isPending) return <LoadingState label="Buscando no catálogo local…" />
  if (search.isError) return <ErrorState error={search.error} onRetry={() => void search.refetch()} />

  const { hits, query: searched } = search.data
  const hasExact = hits.some((h) => h.matchType === 'EXACT')
  const onlyFuzzy = hits.length > 0 && !hasExact

  return (
    <section className={styles.results} aria-labelledby="resultados-titulo" aria-busy={search.isFetching}>
      <h2 id="resultados-titulo" className={styles.resultsTitle}>
        Resultados
      </h2>
      <p className={styles.resultsCount} role="status" aria-live="polite">
        {countLabel(hits.length)} para “{searched}”
        {onlyFuzzy && ' — só grafias parecidas'}
      </p>
      {hits.length > 0 && (
        <ul className={styles.hits}>
          {hits.map((hit) => (
            <SearchResultItem key={hit.slug} hit={hit} />
          ))}
        </ul>
      )}
      {!hasExact && !importInProgress && (
        <ImportOffer key={searched} query={searched} onStarted={onImportStarted} />
      )}
    </section>
  )
}
