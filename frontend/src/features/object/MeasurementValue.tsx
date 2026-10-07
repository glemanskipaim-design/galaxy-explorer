import type { Measurement } from '../../api/types'
import { ExternalLink } from '../../components/ui/ExternalLink'
import { propertyAnchorId, propertyLabel, type MeasurementIndex } from '../../domain/displayGroups'
import { formatDistance } from '../../domain/format/distance'
import { formatMeasurement, limitPrefix } from '../../domain/format/measurement'
import { withUnit } from '../../domain/format/units'
import { EpistemicBadge } from './EpistemicBadge'
import styles from './Object.module.css'
import { ProvenanceLine } from './ProvenanceLine'

interface MeasurementValueProps {
  measurement: Measurement
  propertyCode: string
  byId: MeasurementIndex
}

function Quantity({ limit, value, uncertainty, unit }: { limit: string | null; value: string; uncertainty: string | null; unit: string }) {
  return (
    <span className={styles.quantity}>
      {limit && <span className={styles.limit}>{limit} </span>}
      <span className={styles.value}>{value}</span>
      {uncertainty && <span className={styles.uncertainty}> {uncertainty}</span>}
      {unit && <span className={styles.unit}>{withUnit('', unit)}</span>}
    </span>
  )
}

/** De onde veio um valor calculado: link ancora para a linha da propriedade de origem. */
function DerivedFrom({ ids, byId }: { ids: number[]; byId: MeasurementIndex }) {
  return (
    <>
      {ids.map((id, i) => {
        const origin = byId.get(id)
        return (
          <span key={id}>
            {i > 0 && ', '}
            {origin ? (
              <a href={`#${propertyAnchorId(origin.property)}`}>{propertyLabel(origin.property)}</a>
            ) : (
              `medição nº ${id}`
            )}
          </span>
        )
      })}
    </>
  )
}

/** A origem aparece sempre que a API a informa; so o verbo muda conforme quem calculou. */
function derivationVerb(epistemic: string): string {
  return epistemic === 'computed_by_us' ? 'calculado por nós a partir de' : 'derivado de'
}

export function MeasurementValue({ measurement: m, propertyCode, byId }: MeasurementValueProps) {
  const distance = propertyCode === 'distance' ? formatDistance(m) : null
  const formatted = formatMeasurement(m)
  const limit = limitPrefix(m.limitFlag)
  const derived = m.derivedFrom && m.derivedFrom.length > 0 ? m.derivedFrom : null

  return (
    <div className={styles.measurement} id={`m-${m.id}`}>
      <div className={styles.valueLine}>
        {distance ? (
          <span className={styles.distance}>
            <Quantity limit={limit} {...distance.pc} />
            <Quantity limit={limit} {...distance.ly} />
          </span>
        ) : (
          <Quantity {...formatted} />
        )}
        <EpistemicBadge epistemic={m.epistemic} />
      </div>
      {(derived || m.method) && (
        <p className={styles.method}>
          {derived && (
            <>
              {derivationVerb(m.epistemic)}: <DerivedFrom ids={derived} byId={byId} />
              {m.method && ' · '}
            </>
          )}
          {m.method && (
            <>
              método: <code>{m.method}</code>
            </>
          )}
        </p>
      )}
      <ProvenanceLine source={m.source} reference={m.reference} retrievedAt={m.retrievedAt} />
      <details className={styles.details}>
        <summary>detalhes</summary>
        <dl className={styles.detailsList}>
          <div>
            <dt>Valor original</dt>
            <dd>
              <code>
                {m.originalValue}
                {m.originalUnit && ` ${m.originalUnit}`}
                {m.originalError && ` ± ${m.originalError}`}
              </code>
            </dd>
          </div>
          <div>
            <dt>Conjunto na fonte</dt>
            <dd>
              <code>{m.dataset}</code>
            </dd>
          </div>
          <div>
            <dt>Registro na fonte</dt>
            <dd>
              <ExternalLink href={m.sourceUrl}>abrir em {m.source}</ExternalLink>
            </dd>
          </div>
        </dl>
      </details>
    </div>
  )
}
