import type { Measurement } from '../../api/types'
import { decimalsOf, formatNumber, MAX_SIGNIFICANT_DIGITS, significantDigitsOf } from './number'
import { displayDecimals, formatUncertainty } from './uncertainty'
import { unitSymbol } from './units'

export interface FormattedMeasurement {
  value: string
  uncertainty: string | null
  unit: string
  /** prefixo de limite: "<", ">", "≈" */
  limit: string | null
}

const LIMITS: Record<string, string> = { '<': '<', '>': '>', '~': '≈' }

export function limitPrefix(flag: string | null): string | null {
  if (flag === null) return null
  return LIMITS[flag] ?? flag
}

/**
 * Digitos a exibir sem incerteza: os algarismos significativos do texto ORIGINAL da fonte
 * (invariantes a conversao de escala: "761" kpc e 761000 pc tem 3), 4 se o texto nao for
 * numerico. Teto em MAX_SIGNIFICANT_DIGITS.
 *
 * Desvio do plano (que condicionava a originalUnit = unit): nas magnitudes a fonte manda
 * originalUnit = null e a unidade canonica e "mag"; a condicao exibiria "0,4200" para um "0.42"
 * da fonte — precisao inventada. Algarismo significativo nao depende da escala.
 */
export function significantDigitsFor(m: Measurement): number {
  return Math.min(significantDigitsOf(m.originalValue) ?? 4, MAX_SIGNIFICANT_DIGITS)
}

/** Valor + incerteza + unidade + limite, prontos para exibir. Texto (`valueText`) sai como veio. */
export function formatMeasurement(m: Measurement): FormattedMeasurement {
  const unit = unitSymbol(m.unit)
  const limit = limitPrefix(m.limitFlag)
  if (m.valueText !== null) return { value: m.valueText, uncertainty: null, unit, limit }
  if (m.value === null) return { value: m.originalValue, uncertainty: null, unit, limit }
  // Fora a distancia (que tem formatDistance), a unidade canonica tem a mesma escala da
  // original (km.s-1 = km/s, mas, arcmin, mag): as casas do texto original valem para o exibido.
  const decimals = displayDecimals(m.errMinus, m.errPlus, decimalsOf(m.originalValue))
  if (decimals !== null) {
    return {
      value: formatNumber(m.value, { fractionDigits: decimals }),
      uncertainty: formatUncertainty(m.errMinus, m.errPlus, decimals),
      unit,
      limit,
    }
  }
  return {
    value: formatNumber(m.value, { significantDigits: significantDigitsFor(m) }),
    uncertainty: null,
    unit,
    limit,
  }
}
