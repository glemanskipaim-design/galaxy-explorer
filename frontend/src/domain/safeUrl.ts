/**
 * So aceita URL absoluta `https:`. Qualquer outra coisa (javascript:, data:, http:, relativa,
 * invalida, null) -> null. URLs vem do banco e da API (hoje confiaveis), mas o custo e zero e
 * fecha a classe de bug.
 */
export function safeUrl(raw: string | null): string | null {
  if (raw === null) return null
  try {
    const url = new URL(raw)
    return url.protocol === 'https:' ? url.href : null
  } catch {
    return null
  }
}
