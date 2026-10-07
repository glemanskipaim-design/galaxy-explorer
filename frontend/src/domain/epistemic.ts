export type EpistemicVariant = 'observed' | 'derived' | 'computed' | 'estimated' | 'model' | 'unknown'

export interface EpistemicInfo {
  label: string
  description: string
  variant: EpistemicVariant
}

const KNOWN: Record<string, EpistemicInfo> = {
  observed: {
    label: 'Observado',
    description: 'Medido e publicado pela fonte.',
    variant: 'observed',
  },
  derived_by_source: {
    label: 'Derivado pela fonte',
    description: 'Calculado pela própria fonte a partir de outras medidas.',
    variant: 'derived',
  },
  computed_by_us: {
    label: 'Calculado por nós',
    description: 'Calculado por este site a partir de um valor da fonte; o método está indicado.',
    variant: 'computed',
  },
  estimated: {
    label: 'Estimado',
    description: 'Estimativa publicada, sem medição direta.',
    variant: 'estimated',
  },
  model: {
    label: 'Modelo',
    description: 'Resultado de modelo teórico, não de medição.',
    variant: 'model',
  },
}

/** Codigo -> rotulo/descricao/variante. Desconhecido mostra o codigo cru (nunca quebra). */
export function epistemicInfo(code: string): EpistemicInfo {
  return (
    KNOWN[code] ?? {
      label: code,
      description: 'Natureza não catalogada.',
      variant: 'unknown',
    }
  )
}
