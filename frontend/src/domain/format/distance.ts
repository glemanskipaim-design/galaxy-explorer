import type { Measurement } from '../../api/types'
import { decimalsOf, formatNumber, MAX_SIGNIFICANT_DIGITS, significantDigitsOf } from './number'
import { displayDecimals, formatUncertainty } from './uncertainty'

// ── Constantes (familia IAU, como manda a Fase 0 §6) ────────────────────────────────────────
/** Unidade astronomica, em metros (IAU 2012, Resolucao B2 — valor exato por definicao). */
export const AU_M = 149_597_870_700
/** Parsec = 648 000/π au (IAU 2015, Resolucao B2). */
export const PC_M = (648_000 / Math.PI) * AU_M
/** Ano-luz = c × ano juliano = 299 792 458 m/s × 365,25 × 86 400 s = 9 460 730 472 580 800 m (IAU). */
export const LY_M = 299_792_458 * 365.25 * 86_400
/** 1 pc = 3,261 563 777… anos-luz (derivado das definicoes acima, nao digitado). */
export const LY_PER_PC = PC_M / LY_M

interface Scale {
  divisor: number
  unit: string
}

const PC_SCALES: Scale[] = [
  { divisor: 1e6, unit: 'Mpc' },
  { divisor: 1e3, unit: 'kpc' },
  { divisor: 1, unit: 'pc' },
]

const LY_SCALES: Scale[] = [
  { divisor: 1e9, unit: 'bilhões de anos-luz' },
  { divisor: 1e6, unit: 'milhões de anos-luz' },
  { divisor: 1e3, unit: 'mil anos-luz' },
  { divisor: 1, unit: 'anos-luz' },
]

function pickScale(x: number, scales: Scale[]): Scale {
  const abs = Math.abs(x)
  return scales.find((s) => abs >= s.divisor) ?? (scales[scales.length - 1] as Scale)
}

export interface ScaledQuantity {
  value: string
  uncertainty: string | null
  unit: string
}

function scaled(
  value: number,
  errMinus: number | null,
  errPlus: number | null,
  sigDigits: number,
  scales: Scale[],
  source: { unit: string | null; decimals: number | null } | null,
): ScaledQuantity {
  const { divisor, unit } = pickScale(value, scales)
  const v = value / divisor
  const em = errMinus === null ? null : errMinus / divisor
  const ep = errPlus === null ? null : errPlus / divisor
  // casas do texto original so valem se a escala exibida for a mesma da fonte (761 kpc -> kpc)
  const sourceDecimals = source && source.unit === unit ? source.decimals : null
  const decimals = displayDecimals(em, ep, sourceDecimals)
  if (decimals !== null) {
    return {
      value: formatNumber(v, { fractionDigits: decimals }),
      uncertainty: formatUncertainty(em, ep, decimals),
      unit,
    }
  }
  return { value: formatNumber(v, { significantDigits: sigDigits }), uncertainty: null, unit }
}

export interface FormattedDistance {
  pc: ScaledQuantity
  ly: ScaledQuantity
}

/**
 * Distancia (valor canonico em pc) em pc/kpc/Mpc E em anos-luz, com escala automatica.
 * A incerteza e convertida pelo mesmo fator (conversao linear). Sem incerteza, usa os
 * algarismos significativos do texto original (invariantes a mudanca de escala).
 * Retorna null se a medicao nao for numerica ou se a unidade canonica nao for pc.
 */
export function formatDistance(m: Measurement): FormattedDistance | null {
  // so converte o que sabidamente esta em pc; outra unidade cai no formatMeasurement (unidade real)
  if (m.value === null || m.unit !== 'pc') return null
  const sig = Math.min(significantDigitsOf(m.originalValue) ?? 4, MAX_SIGNIFICANT_DIGITS)
  return {
    pc: scaled(m.value, m.errMinus, m.errPlus, sig, PC_SCALES, {
      unit: m.originalUnit,
      decimals: decimalsOf(m.originalValue),
    }),
    ly: scaled(
      m.value * LY_PER_PC,
      m.errMinus === null ? null : m.errMinus * LY_PER_PC,
      m.errPlus === null ? null : m.errPlus * LY_PER_PC,
      sig,
      LY_SCALES,
      null,
    ),
  }
}
