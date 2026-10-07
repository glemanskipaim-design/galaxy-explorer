import { useId } from 'react'
import type { Property } from '../../api/types'
import type { MeasurementIndex } from '../../domain/displayGroups'
import styles from './Object.module.css'
import { PropertyRow } from './PropertyRow'

interface PropertyGroupCardProps {
  label: string
  properties: Property[]
  byId: MeasurementIndex
}

export function PropertyGroupCard({ label, properties, byId }: PropertyGroupCardProps) {
  const titleId = useId()
  return (
    <section className={styles.card} aria-labelledby={titleId}>
      <h2 id={titleId} className={styles.cardTitle}>
        {label}
      </h2>
      <dl className={styles.rows}>
        {properties.map((p) => (
          <PropertyRow key={`${p.code}/${p.qualifier}`} property={p} byId={byId} />
        ))}
      </dl>
    </section>
  )
}
