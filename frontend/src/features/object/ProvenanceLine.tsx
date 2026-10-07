import type { Reference } from '../../api/types'
import { ExternalLink } from '../../components/ui/ExternalLink'
import { formatRetrievedAt } from '../../domain/format/date'
import styles from './Object.module.css'

interface ProvenanceLineProps {
  source: string
  reference: Reference | null
  retrievedAt: string
}

/**
 * "Fonte: SIMBAD · Ref.: <bibcode> · coletado em dd/mm/aaaa". O link ADS e o `adsUrl` COMO VEIO
 * da API (bibcode ja encoded) — nunca reconstruido aqui.
 */
export function ProvenanceLine({ source, reference, retrievedAt }: ProvenanceLineProps) {
  return (
    <p className={styles.provenance}>
      <span>Fonte: {source}</span>
      <span aria-hidden="true" className={styles.sep}>
        ·
      </span>
      {reference ? (
        <span>
          Ref.: <ExternalLink href={reference.adsUrl}>{reference.bibcode}</ExternalLink>
        </span>
      ) : (
        <span>sem referência bibliográfica na fonte</span>
      )}
      <span aria-hidden="true" className={styles.sep}>
        ·
      </span>
      <span>coletado em {formatRetrievedAt(retrievedAt)}</span>
    </p>
  )
}
