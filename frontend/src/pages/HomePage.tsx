import { useSearchParams } from 'react-router'
import { CategoryShortcuts } from '../features/home/CategoryShortcuts'
import { Hero } from '../features/home/Hero'
import { ImportProgress } from '../features/search/ImportProgress'
import { SearchBox } from '../features/search/SearchBox'
import { SearchResults } from '../features/search/SearchResults'
import styles from './Pages.module.css'

/** Estado na URL: ?q=<termo> e ?import=<jobId> — recarregar a pagina retoma a busca/importacao. */
export function HomePage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const importParam = params.get('import')
  const importId = importParam !== null && /^\d{1,15}$/.test(importParam) ? Number(importParam) : null

  const onChange = (value: string) => {
    // digitar troca o termo (e descarta a importacao anterior); replace nao polui o historico
    const next = new URLSearchParams()
    if (value) next.set('q', value)
    setParams(next, { replace: true })
  }

  const onImportStarted = (jobId: number) => {
    const next = new URLSearchParams(params)
    next.set('import', String(jobId))
    setParams(next)
  }

  return (
    <div className={styles.home}>
      <title>{q ? `${q} · Galaxy Explorer` : 'Galaxy Explorer — o universo em dados reais'}</title>
      <section className={styles.homeTop} aria-label="Busca">
        <Hero />
        <div className={styles.searchWrap}>
          {/* a home E a busca: foco direto no campo (pedido do plano); SkipLink continua o 1o Tab */}
          {/* eslint-disable-next-line jsx-a11y/no-autofocus */}
          <SearchBox variant="hero" value={q} onChange={onChange} autoFocus />
        </div>
      </section>
      <div className={styles.homeResults}>
        <SearchResults query={q} importInProgress={importId !== null} onImportStarted={onImportStarted} />
        {importId !== null && <ImportProgress key={importId} jobId={importId} />}
      </div>
      <CategoryShortcuts />
    </div>
  )
}
