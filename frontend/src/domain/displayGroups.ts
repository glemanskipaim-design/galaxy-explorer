import type { Measurement, Property } from '../api/types'

/** Ordem fixa e rotulo dos grupos (property_definition.display_group, V3). */
const GROUPS: ReadonlyArray<{ code: string; label: string }> = [
  { code: 'astrometry', label: 'Astrometria' },
  { code: 'kinematics', label: 'Cinemática' },
  { code: 'photometry', label: 'Fotometria' },
  { code: 'classification', label: 'Classificação' },
  { code: 'morphology', label: 'Morfologia' },
]

export interface PropertyGroup {
  group: string
  label: string
  properties: Property[]
}

/** Agrupa na ordem fixa; grupo desconhecido vai ao fim com o codigo cru como rotulo (nunca some). */
export function groupProperties(props: Property[]): PropertyGroup[] {
  const byGroup = new Map<string, Property[]>()
  for (const p of props) {
    const list = byGroup.get(p.group) ?? []
    list.push(p)
    byGroup.set(p.group, list)
  }
  const known = GROUPS.filter((g) => byGroup.has(g.code)).map((g) => ({
    group: g.code,
    label: g.label,
    properties: byGroup.get(g.code) ?? [],
  }))
  const unknown = [...byGroup.keys()]
    .filter((code) => !GROUPS.some((g) => g.code === code))
    .map((code) => ({ group: code, label: code, properties: byGroup.get(code) ?? [] }))
  return [...known, ...unknown]
}

/** Rotulo com qualificador: "Magnitude aparente (V)". */
export function propertyLabel(p: Pick<Property, 'label' | 'qualifier'>): string {
  return p.qualifier ? `${p.label} (${p.qualifier})` : p.label
}

/** Id estavel da linha da propriedade (ancora para "calculado a partir de"). */
export function propertyAnchorId(p: Pick<Property, 'code' | 'qualifier'>): string {
  return `prop-${p.code}-${p.qualifier || 'x'}`.replace(/[^A-Za-z0-9_-]/g, '_')
}

export type MeasurementIndex = ReadonlyMap<number, { property: Property; measurement: Measurement }>

/** id da medicao -> (propriedade, medicao), preferidas e alternativas. Montado uma vez por pagina. */
export function indexMeasurements(props: Property[]): MeasurementIndex {
  const index = new Map<number, { property: Property; measurement: Measurement }>()
  for (const property of props) {
    for (const measurement of [property.preferred, ...property.alternatives]) {
      if (measurement) index.set(measurement.id, { property, measurement })
    }
  }
  return index
}
