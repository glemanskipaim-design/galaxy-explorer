import type { Identifier } from '../api/types'

/**
 * Nome comum para exibir em destaque: tira o prefixo de convencao "NAME " do SIMBAD
 * ("NAME Andromeda" -> "Andromeda"; e marcacao da fonte, nao parte do nome). O identificador
 * completo continua listado como veio.
 */
export function commonNameLabel(identifier: string): string {
  return identifier.replace(/^NAME\s+/, '')
}

export interface IdentifierGroup {
  catalog: string
  identifiers: Identifier[]
}

/** Agrupa por catalogo, na ordem em que a API devolveu; catalogo null vira "Outros". */
export function groupByCatalog(identifiers: Identifier[]): IdentifierGroup[] {
  const groups = new Map<string, Identifier[]>()
  for (const id of identifiers) {
    const key = id.catalog ?? 'Outros'
    const list = groups.get(key) ?? []
    list.push(id)
    groups.set(key, list)
  }
  return [...groups.entries()].map(([catalog, list]) => ({ catalog, identifiers: list }))
}
