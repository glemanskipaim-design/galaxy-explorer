import type { CategorySummary } from '../api/types'

export interface CategoryShortcut {
  label: string
  /** codigo da categoria RAIZ no backend; null = nao existe categoria ainda */
  categoryCode: string | null
  /** por que esta desabilitado quando nao ha dados */
  pendingNote: string
}

/**
 * Atalhos da home, na ordem da especificacao. Mapeamento 1:1 codigo -> atalho; nenhuma
 * reinterpretacao de categoria no frontend (AGN fica fora de "Galaxias"; Sgr A* e classificado
 * como fonte de raios X, entao "Buracos Negros" fica "em breve" — decisao do usuario).
 */
export const CATEGORY_SHORTCUTS: readonly CategoryShortcut[] = [
  { label: 'Estrelas', categoryCode: 'star', pendingNote: 'em breve' },
  { label: 'Planetas', categoryCode: null, pendingNote: 'em breve' },
  { label: 'Galáxias', categoryCode: 'galaxy', pendingNote: 'em breve' },
  { label: 'Nebulosas', categoryCode: 'nebula', pendingNote: 'em breve' },
  { label: 'Aglomerados', categoryCode: 'cluster', pendingNote: 'em breve' },
  { label: 'Cometas', categoryCode: null, pendingNote: 'em breve' },
  { label: 'Asteroides', categoryCode: null, pendingNote: 'em breve' },
  { label: 'Exoplanetas', categoryCode: 'exoplanet', pendingNote: 'em breve' },
  { label: 'Buracos Negros', categoryCode: 'black_hole', pendingNote: 'em breve' },
]

export interface ResolvedShortcut {
  label: string
  to: string | null
  count: number | null
  disabledReason: string | null
}

const UNAVAILABLE = 'indisponível'
/** Codigo esperado que a API nao devolveu: divergencia de contrato, nao "em breve". */
const MISSING_IN_API = 'categoria não encontrada no catálogo'

/**
 * Ativo SSE o codigo existe na resposta da API E tem objetos. Tres motivos distintos para
 * desabilitar, sem misturar:
 * - sem codigo (categoria que sabidamente ainda nao existe no backend) ou existe com 0 objetos
 *   -> `pendingNote` ("em breve");
 * - codigo esperado AUSENTE da resposta (ex.: renomeado no backend) -> "categoria nao encontrada";
 * - resposta null (erro de rede/API) -> todos "indisponivel": nunca ativar no escuro.
 */
export function resolveShortcuts(
  shortcuts: readonly CategoryShortcut[],
  categories: CategorySummary[] | null,
): ResolvedShortcut[] {
  return shortcuts.map((s) => {
    if (categories === null) return { label: s.label, to: null, count: null, disabledReason: UNAVAILABLE }
    if (s.categoryCode === null) return { label: s.label, to: null, count: null, disabledReason: s.pendingNote }
    const found = categories.find((c) => c.code === s.categoryCode)
    if (!found) return { label: s.label, to: null, count: null, disabledReason: MISSING_IN_API }
    if (found.objectCount <= 0) {
      return { label: s.label, to: null, count: null, disabledReason: s.pendingNote }
    }
    return {
      label: s.label,
      to: `/categories/${encodeURIComponent(found.code)}`,
      count: found.objectCount,
      disabledReason: null,
    }
  })
}
