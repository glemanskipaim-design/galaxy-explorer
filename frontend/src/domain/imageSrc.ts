const FILE = /^\/api\/v1\/images\/files\/[0-9a-f]{64}\.jpg$/

/**
 * So aceita o caminho de arquivo da propria API (mesma origem). Qualquer outra coisa (URL de
 * terceiro, javascript:, data:) -> null. Junto com o CSP `img-src 'self'` do index.html, garante
 * que o navegador nunca busca imagem fora do site (spec §12).
 */
export function safeImageSrc(url: string | null | undefined): string | null {
  return typeof url === 'string' && FILE.test(url) ? url : null
}
