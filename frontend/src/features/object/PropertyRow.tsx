import type { Property } from '../../api/types'
import { propertyAnchorId, propertyLabel, type MeasurementIndex } from '../../domain/displayGroups'
import { AbsenceNotice } from './AbsenceNotice'
import { AlternativeValues } from './AlternativeValues'
import { MeasurementValue } from './MeasurementValue'
import styles from './Object.module.css'

/**
 * Uma propriedade. Indisponivel -> motivo. Disponivel SEM preferido -> diz isso e abre as
 * alternativas: nunca promover um valor silenciosamente.
 */
export function PropertyRow({ property: p, byId }: { property: Property; byId: MeasurementIndex }) {
  return (
    <div className={styles.row} id={propertyAnchorId(p)}>
      <dt className={styles.rowLabel}>{propertyLabel(p)}</dt>
      <dd className={styles.rowValue}>
        {!p.available ? (
          <AbsenceNotice absence={p.absence} />
        ) : (
          <>
            {p.preferred ? (
              <MeasurementValue measurement={p.preferred} propertyCode={p.code} byId={byId} />
            ) : (
              <p className={styles.noPreferred}>Sem valor preferido definido</p>
            )}
            <AlternativeValues
              preferred={p.preferred}
              alternatives={p.alternatives}
              propertyCode={p.code}
              byId={byId}
            />
          </>
        )}
      </dd>
    </div>
  )
}
