import type { ImageKind } from '../api/types'

export interface ImageKindInfo {
  label: string
  description: string
  /** estilo do selo; `warning` = nao e fotografia */
  variant: 'photo' | 'survey' | 'warning' | 'unknown'
}

const KINDS: Record<string, ImageKindInfo> = {
  processed_composite: {
    label: 'Imagem processada',
    description: 'Composição colorida de dados reais do telescópio, processada para divulgação.',
    variant: 'photo',
  },
  survey_cutout: {
    label: 'Recorte de survey',
    description: 'Recorte automático de um levantamento do céu, centrado na posição catalogada.',
    variant: 'survey',
  },
  observation: {
    label: 'Observação',
    description: 'Imagem de observação da missão.',
    variant: 'photo',
  },
  artist_impression: {
    label: 'Ilustração artística — não é fotografia',
    description: 'Desenho feito por artista; não mostra o objeto como ele foi observado.',
    variant: 'warning',
  },
  diagram: {
    label: 'Diagrama',
    description: 'Esquema ou gráfico; não é fotografia.',
    variant: 'warning',
  },
}

/** Tipo desconhecido (valor novo no backend): mostra o codigo cru, nunca finge ser foto. */
export function imageKindInfo(kind: ImageKind): ImageKindInfo {
  return (
    KINDS[kind] ?? {
      label: kind,
      description: 'Tipo de imagem não catalogado nesta versão do site.',
      variant: 'unknown',
    }
  )
}
