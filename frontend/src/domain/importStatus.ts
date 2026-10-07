import type { ImportJob, ImportJobStatus } from '../api/types'

export type ImportTone = 'progress' | 'success' | 'notFound' | 'error'

export interface ImportStatusView {
  tone: ImportTone
  text: string
  terminal: boolean
}

const TERMINAL: ReadonlySet<string> = new Set(['succeeded', 'not_found', 'failed'])

export function isTerminal(status: ImportJobStatus): boolean {
  return TERMINAL.has(status)
}

/**
 * Mensagem de cada status do job. O backend NAO expoe a fase (Sesame vs SIMBAD acontecem os
 * dois dentro de `running`), entao `running` tem uma mensagem unica que nomeia as duas etapas —
 * trocar o texto por tempo decorrido seria inventar progresso (decisao do usuario, adendo do plano).
 * Em not_found/failed a mensagem e a da API (ja e so a categoria do erro, sem detalhe interno).
 */
export function importStatusView(job: ImportJob): ImportStatusView {
  switch (job.status) {
    case 'queued':
      return { tone: 'progress', text: 'Na fila de importação…', terminal: false }
    case 'running':
      return {
        tone: 'progress',
        text: 'Resolvendo o nome no Sesame (CDS) e importando de SIMBAD…',
        terminal: false,
      }
    case 'succeeded':
      return {
        tone: 'success',
        text: job.message ? `Concluído: ${job.message}.` : 'Importação concluída.',
        terminal: true,
      }
    case 'not_found':
      return { tone: 'notFound', text: job.message ?? 'Objeto não encontrado.', terminal: true }
    case 'failed':
      return { tone: 'error', text: job.message ?? 'A importação falhou.', terminal: true }
    default:
      return { tone: 'progress', text: `Status da importação: ${job.status}`, terminal: false }
  }
}
