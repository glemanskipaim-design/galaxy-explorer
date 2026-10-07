// Formatacao numerica pt-BR (virgula decimal). Puro: sem React, sem fetch.

const SUPERSCRIPT: Record<string, string> = {
  '0': '⁰', '1': '¹', '2': '²', '3': '³', '4': '⁴',
  '5': '⁵', '6': '⁶', '7': '⁷', '8': '⁸', '9': '⁹', '-': '⁻',
}

/** Teto de algarismos significativos exibidos quando nao ha incerteza: valores calculados pela
 * fonte chegam com 16-17 algarismos de ponto flutuante, que nao sao precisao real. O texto
 * original completo continua visivel em "detalhes". */
export const MAX_SIGNIFICANT_DIGITS = 7

const SCI_UPPER = 1e6
const SCI_LOWER = 1e-4

/** Sinal de menos tipografico (U+2212), mais legivel que o hifen. */
const MINUS = '−'

function superscript(n: number): string {
  return String(n)
    .split('')
    .map((c) => SUPERSCRIPT[c] ?? c)
    .join('')
}

function intl(opts: Intl.NumberFormatOptions): Intl.NumberFormat {
  return new Intl.NumberFormat('pt-BR', { useGrouping: true, ...opts })
}

function fixMinus(s: string): string {
  return s.replace(/^-/, MINUS)
}

export interface NumberFormatOptions {
  /** algarismos significativos (usado quando nao ha incerteza) */
  significantDigits?: number
  /** casas decimais fixas (usado quando ha incerteza: mesmo nº de casas do ±) */
  fractionDigits?: number
}

/**
 * Formata em pt-BR. Com `significantDigits`, valores acima de 1e6 ou abaixo de 1e-4 (em modulo)
 * viram notacao cientifica legivel: `4,503 × 10⁻⁵`.
 */
export function formatNumber(x: number, opts: NumberFormatOptions = {}): string {
  if (opts.fractionDigits !== undefined) {
    const d = Math.max(0, Math.min(20, opts.fractionDigits))
    return fixMinus(intl({ minimumFractionDigits: d, maximumFractionDigits: d }).format(x))
  }
  const sig = Math.max(1, Math.min(21, opts.significantDigits ?? 4))
  const abs = Math.abs(x)
  if (abs !== 0 && (abs >= SCI_UPPER || abs < SCI_LOWER)) {
    let exp = Math.floor(Math.log10(abs))
    let mantissa = x / 10 ** exp
    // arredondar pode levar 9,999 a 10,00: reajusta o expoente
    const rounded = Number(mantissa.toPrecision(sig))
    if (Math.abs(rounded) >= 10) {
      exp += 1
      mantissa = x / 10 ** exp
    }
    const m = intl({ minimumSignificantDigits: sig, maximumSignificantDigits: sig }).format(mantissa)
    return `${fixMinus(m)} × 10${superscript(exp)}`
  }
  return fixMinus(
    intl({ minimumSignificantDigits: sig, maximumSignificantDigits: sig }).format(x),
  )
}

/**
 * Casas decimais escritas no texto original ("6.55" -> 2, "761" -> 0). Notacao com expoente
 * ou texto nao numerico -> null (nao da para comparar escala).
 */
export function decimalsOf(original: string): number | null {
  const m = /^\s*[+-]?\d*(?:\.(\d*))?\s*$/.exec(original)
  if (!m || !/\d/.test(original)) return null
  return (m[1] ?? '').length
}

/**
 * Algarismos significativos do texto ORIGINAL da fonte ("6.55" -> 3, "0.373" -> 3,
 * "7.308656408078207E-5" -> 16, "761" -> 3). Zeros a direita sem ponto decimal ("20") contam
 * como significativos (a fonte nao distingue). Texto nao numerico -> null.
 */
export function significantDigitsOf(original: string): number | null {
  const m = /^\s*[+-]?(\d*)(?:\.(\d*))?(?:[eE][+-]?\d+)?\s*$/.exec(original)
  if (!m) return null
  const intPart = m[1] ?? ''
  const fracPart = m[2] ?? ''
  if (intPart === '' && fracPart === '') return null
  const digits = (intPart + fracPart).replace(/^0+/, '')
  if (digits === '') return 1 // o valor e zero
  return digits.length
}
