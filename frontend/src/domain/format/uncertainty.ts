import { formatNumber } from './number'

/**
 * Casas decimais ditadas pela incerteza: 2 algarismos significativos da MENOR incerteza
 * positiva. O valor e exibido com o mesmo numero de casas. Nunca negativo (11000 -> 0 casas).
 * Sem incerteza utilizavel -> null.
 */
export function decimalsForUncertainty(errMinus: number | null, errPlus: number | null): number | null {
  const candidates = [errMinus, errPlus].filter((e): e is number => e !== null && e > 0)
  if (candidates.length === 0) return null
  const smallest = Math.min(...candidates)
  return Math.max(0, 1 - Math.floor(Math.log10(smallest)))
}

/**
 * Casas a exibir: 2 algarismos significativos da incerteza, mas nunca mais casas do que o
 * valor publicado pela fonte tinha (`sourceDecimals`, na mesma escala do numero exibido) — a
 * fonte diz "−300 ± 4", exibir "−300,0 ± 4,0" seria precisao inventada. Piso: 1 algarismo
 * significativo da incerteza (senao o ± arredondaria para zero).
 */
export function displayDecimals(
  errMinus: number | null,
  errPlus: number | null,
  sourceDecimals: number | null,
): number | null {
  const twoSig = decimalsForUncertainty(errMinus, errPlus)
  if (twoSig === null || sourceDecimals === null) return twoSig
  const oneSig = Math.max(0, twoSig - 1)
  return Math.min(twoSig, Math.max(oneSig, sourceDecimals))
}

/**
 * `± x` quando simetrica; `+a / −b` quando assimetrica; um lado so -> `+a` ou `−b`.
 * Ambos null -> null.
 */
export function formatUncertainty(
  errMinus: number | null,
  errPlus: number | null,
  fractionDigits: number,
): string | null {
  const f = (x: number) => formatNumber(Math.abs(x), { fractionDigits })
  if (errMinus === null && errPlus === null) return null
  if (errMinus !== null && errPlus !== null) {
    if (f(errMinus) === f(errPlus)) return `± ${f(errPlus)}`
    return `+${f(errPlus)} / −${f(errMinus)}`
  }
  if (errPlus !== null) return `+${f(errPlus)}`
  return `−${f(errMinus ?? 0)}`
}
