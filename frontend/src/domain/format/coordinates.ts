import { formatNumber } from './number'

const pad2 = (n: number) => String(n).padStart(2, '0')

/** Ascensao reta em graus -> `00h 42m 44,3s` (segundos com 1 casa). */
export function formatRa(deg: number): string {
  const normalized = ((deg % 360) + 360) % 360
  // trabalha em decimos de segundo de tempo para o arredondamento propagar (59,96s -> 1m)
  let tenths = Math.round((normalized / 15) * 36_000)
  tenths %= 24 * 36_000
  const h = Math.floor(tenths / 36_000)
  const m = Math.floor((tenths % 36_000) / 600)
  const s = (tenths % 600) / 10
  const sText = formatNumber(s, { fractionDigits: 1 }).padStart(4, '0')
  return `${pad2(h)}h ${pad2(m)}m ${sText}s`
}

/** Declinacao em graus -> `+41° 16′ 07,5″` (segundos de arco com 1 casa). */
export function formatDec(deg: number): string {
  const sign = deg < 0 ? '−' : '+'
  const tenths = Math.round(Math.abs(deg) * 36_000)
  const d = Math.floor(tenths / 36_000)
  const m = Math.floor((tenths % 36_000) / 600)
  const s = (tenths % 600) / 10
  const sText = formatNumber(s, { fractionDigits: 1 }).padStart(4, '0')
  return `${sign}${pad2(d)}° ${pad2(m)}′ ${sText}″`
}

/** Graus decimais com 5 casas (precisao de ~0,04″), para quem quer o numero cru. */
export function formatDegrees(deg: number): string {
  return `${formatNumber(deg, { fractionDigits: 5 })}°`
}
