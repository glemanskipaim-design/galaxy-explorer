import { useEffect, useState } from 'react'
import { ApiRequestError } from '../../api/http'
import { useCreateImport } from '../../api/queries'
import { errorMessage } from '../../components/ui/errorMessage'
import styles from './Search.module.css'

interface ImportOfferProps {
  query: string
  onStarted: (jobId: number) => void
}

/**
 * Botao EXPLICITO de importacao. Nunca dispara sozinho: cada import consulta um servico externo
 * (CDS), e disparar a cada tecla seria abuso. 429 desabilita o botao pelo tempo do Retry-After.
 */
export function ImportOffer({ query, onStarted }: ImportOfferProps) {
  const create = useCreateImport()
  const [retryUntil, setRetryUntil] = useState<number | null>(null)
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (retryUntil === null) return
    const t = setInterval(() => {
      const current = Date.now()
      setNow(current)
      if (current >= retryUntil) setRetryUntil(null)
    }, 1000)
    return () => clearInterval(t)
  }, [retryUntil])

  const waitSeconds = retryUntil === null ? 0 : Math.max(0, Math.ceil((retryUntil - now) / 1000))
  const queueFull = create.error instanceof ApiRequestError && create.error.status === 429

  const start = () => {
    create.mutate(query, {
      onSuccess: (job) => onStarted(job.id),
      onError: (error) => {
        if (error instanceof ApiRequestError && error.status === 429 && error.retryAfterSeconds !== null) {
          const current = Date.now()
          setNow(current)
          setRetryUntil(current + error.retryAfterSeconds * 1000)
        }
      },
    })
  }

  return (
    <div className={styles.offer}>
      <div className={styles.offerText}>
        <p className={styles.offerTitle}>Não está no catálogo local?</p>
        <p className={styles.offerHint}>
          Podemos procurar “{query}” no SIMBAD (CDS, Strasbourg) e importar os dados com a fonte de
          cada valor. Leva alguns segundos.
        </p>
      </div>
      <button
        type="button"
        className={styles.offerButton}
        onClick={start}
        disabled={create.isPending || waitSeconds > 0}
      >
        {create.isPending ? 'Enviando…' : `Buscar “${query}” no SIMBAD e importar`}
      </button>
      {create.isError && (
        <p className={styles.offerError} role="alert">
          {queueFull && waitSeconds > 0
            ? `Fila de importação cheia. Tente de novo em ${waitSeconds} s.`
            : queueFull
              ? 'Fila de importação cheia. Tente de novo.'
              : errorMessage(create.error)}
        </p>
      )}
    </div>
  )
}
