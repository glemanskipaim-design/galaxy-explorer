import type { ImageKind } from '../api/types'
import { imageKindInfo } from './imageKind'

/**
 * Texto alternativo honesto: diz O QUE a imagem e (tipo + quem forneceu), sem descrever conteudo que
 * nao conhecemos ("galaxia espiral azulada" seria inventado).
 */
export function imageAlt(displayName: string, kind: ImageKind, provider: string): string {
  return `${displayName} — ${imageKindInfo(kind).label} (${provider})`
}
