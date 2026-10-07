import type { Measurement } from '../api/types'

export type ConflictRule = '3sigma' | '10pct' | 'insufficient'

export interface ConflictResult {
  diverges: boolean
  /** regra que decidiu: a do primeiro par divergente; sem divergencia, a mais fraca usada */
  rule: ConflictRule
}

/** σ de uma medicao: media de errMinus/errPlus disponiveis; sem incerteza -> null. */
function sigmaOf(m: Measurement): number | null {
  const errs = [m.errMinus, m.errPlus].filter((e): e is number => e !== null && e >= 0)
  if (errs.length === 0) return null
  return errs.reduce((a, b) => a + b, 0) / errs.length
}

/**
 * Politica de exibicao de conflito da Fase 0 §7: "se dois valores diferem mais que 3σ
 * combinados (ou >10% quando nao ha incerteza), a UI mostra 'Fontes divergem'".
 *
 * Leitura adotada (a unica coerente com o exemplo real que a propria Fase 0 da — redshift de
 * M31, SIMBAD −0,0010002 sem incerteza vs NED −0,000991 ± 0,000003, citado como conflito):
 * - se ao menos um dos dois valores tem incerteza: diverge se |a−b| > 3·√(σa²+σb²), com σ
 *   ausente contando como 0;
 * - se nenhum tem incerteza: diverge se a diferenca relativa (sobre o maior modulo) > 10%.
 *
 * O primeiro valor e o preferido; cada alternativa e comparada com ele.
 * Menos de 2 valores numericos -> `insufficient`.
 */
export function detectConflict(values: Measurement[]): ConflictResult {
  const numeric = values.filter((m) => m.value !== null && m.limitFlag === null)
  const [first, ...others] = numeric
  if (!first || others.length === 0) return { diverges: false, rule: 'insufficient' }
  let weakest: ConflictRule = '3sigma'
  for (const other of others) {
    const a = first.value as number
    const b = other.value as number
    const sa = sigmaOf(first)
    const sb = sigmaOf(other)
    if (sa !== null || sb !== null) {
      const combined = Math.sqrt((sa ?? 0) ** 2 + (sb ?? 0) ** 2)
      if (Math.abs(a - b) > 3 * combined) return { diverges: true, rule: '3sigma' }
    } else {
      weakest = '10pct'
      const scale = Math.max(Math.abs(a), Math.abs(b))
      if (scale > 0 && Math.abs(a - b) / scale > 0.1) return { diverges: true, rule: '10pct' }
    }
  }
  return { diverges: false, rule: weakest }
}
