const FORMAT = new Intl.DateTimeFormat('pt-BR', {
  timeZone: 'UTC',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

/** Data da coleta (ISO-8601) -> dd/mm/aaaa em UTC (data da coleta, nao a hora local). Invalida -> texto cru. */
export function formatRetrievedAt(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return FORMAT.format(d)
}
