/**
 * Paginacao da listagem de categoria (1-based). Puro: recebe os limites como parametro.
 */

/** `?page=` -> numero de pagina; ausente, vazio, nao numerico ou < 1 -> 1. */
export function parsePage(raw: string | null): number {
  if (raw === null || !/^\d+$/.test(raw)) return 1
  const n = Number(raw)
  return Number.isSafeInteger(n) && n >= 1 ? n : 1
}

/** Maior pagina cujo offset o backend aceita: (p - 1) * pageSize <= maxOffset. */
export function maxRequestablePage(pageSize: number, maxOffset: number): number {
  return Math.floor(maxOffset / pageSize) + 1
}

/** Total de paginas para `total` itens (pelo menos 1: categoria vazia ainda tem a pagina 1). */
export function pageCountOf(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / pageSize))
}
