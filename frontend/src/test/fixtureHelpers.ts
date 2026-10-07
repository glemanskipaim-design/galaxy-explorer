import type { Measurement, ObjectDetail, Property } from '../api/types'
import type { Fixture } from './fixtures'

/** Propriedade real de uma fixture de objeto; falha alto se nao existir (nada de fallback inventado). */
export function propertyOf(fixture: Fixture<ObjectDetail>, code: string, qualifier = ''): Property {
  const p = fixture.body.properties.find((x) => x.code === code && x.qualifier === qualifier)
  if (!p) throw new Error(`fixture ${fixture.body.slug} sem propriedade ${code}/${qualifier}`)
  return p
}

/** Valor preferido real de uma propriedade; falha se nao houver. */
export function preferredOf(fixture: Fixture<ObjectDetail>, code: string, qualifier = ''): Measurement {
  const m = propertyOf(fixture, code, qualifier).preferred
  if (!m) throw new Error(`fixture ${fixture.body.slug}: ${code}/${qualifier} sem preferido`)
  return m
}
