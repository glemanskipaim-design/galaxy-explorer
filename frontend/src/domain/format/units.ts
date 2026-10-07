// Codigo de unidade canonica (property_definition.canonical_unit, V3) -> simbolo exibido.
const SYMBOLS: Record<string, string> = {
  pc: 'pc',
  mas: 'mas',
  'km/s': 'km/s',
  mag: 'mag',
  arcmin: '′',
}

/** Desconhecido -> o proprio codigo (nunca some); null -> ''. */
export function unitSymbol(code: string | null): string {
  if (code === null) return ''
  return SYMBOLS[code] ?? code
}

/** Simbolos colados ao numero (sem espaco): 199,53′. */
const ATTACHED = new Set(['′', '″', '°'])

/** Junta valor e unidade com o espacamento tipografico correto. */
export function withUnit(value: string, unit: string): string {
  if (unit === '') return value
  return ATTACHED.has(unit) ? `${value}${unit}` : `${value} ${unit}`
}
