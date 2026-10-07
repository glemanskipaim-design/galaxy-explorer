import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiRequestError } from '../../api/http'
import { useImportJob } from '../../api/queries'
import { cx } from '../../components/ui/cx'
import { errorMessage } from '../../components/ui/errorMessage'
import { TIMINGS } from '../../config'
import { importStatusView, type ImportTone } from '../../domain/importStatus'
import styles from './Search.module.css'

const TONE_CLASS: Record<ImportTone, string | undefined> = {
  progress: styles.toneProgress,
  success: styles.toneSuccess,
  notFound: styles.toneNotFound,
  error: styles.toneError,
}

/** Polling do job; mensagens por status; navega para o objeto quando ha exatamente 1 resultado. */
export function ImportProgress({ jobId }: { jobId: number }) {
  const [timedOut, setTimedOut] = useState(false)
  useEffect(() => {
    const t = setTimeout(() => setTimedOut(true), TIMINGS.importPollMaxMs)
    return () => clearTimeout(t)
  }, [jobId])

  const job = useImportJob(jobId, !timedOut)
  const navigate = useNavigate()
  const data = job.data

  useEffect(() => {
    if (!data || data.status !== 'succeeded' || data.resultSlugs.length !== 1) return
    const slug = data.resultSlugs[0] ?? ''
    // com mensagem ("ja existia no catalogo"), deixa a mensagem visivel antes de navegar
    const delay = data.message ? TIMINGS.importRedirectDelayMs : 0
    const t = setTimeout(() => void navigate(`/objects/${encodeURIComponent(slug)}`), delay)
    return () => clearTimeout(t)
  }, [data, navigate])

  let tone: ImportTone = 'progress'
  let text: string
  if (job.isError) {
    tone = 'error'
    text =
      job.error instanceof ApiRequestError && job.error.status === 404
        ? 'Importação não encontrada (pode ter expirado).'
        : errorMessage(job.error)
  } else if (!data) {
    text = 'Consultando a importação…'
  } else {
    const view = importStatusView(data)
    tone = view.tone
    text = view.text
    if (!view.terminal && timedOut) {
      text = 'Ainda processando — tente novamente mais tarde.'
    }
  }
  const active = tone === 'progress' && !timedOut

  return (
    <section className={cx(styles.progress, TONE_CLASS[tone])} aria-labelledby="import-titulo">
      <h2 id="import-titulo" className={styles.progressTitle}>
        {data ? `Importação de “${data.query}”` : 'Importação'}
      </h2>
      <p role="status" aria-live="polite" className={styles.progressText}>
        {text}
      </p>
      {active && <div className={styles.progressBar} aria-hidden="true" />}
      {data?.status === 'succeeded' && data.resultSlugs.length === 1 && (
        <p className={styles.progressHint}>
          Abrindo a página do objeto…{' '}
          <Link to={`/objects/${encodeURIComponent(data.resultSlugs[0] ?? '')}`}>ir agora</Link>
        </p>
      )}
      {data?.status === 'succeeded' && data.resultSlugs.length > 1 && (
        <ul className={styles.progressList}>
          {data.resultSlugs.map((slug) => (
            <li key={slug}>
              <Link to={`/objects/${encodeURIComponent(slug)}`}>{slug}</Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
