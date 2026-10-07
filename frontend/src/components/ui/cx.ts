/** Junta nomes de classe ignorando vazios (CSS Modules devolve `string | undefined`). */
export function cx(...classes: Array<string | undefined | null | false>): string {
  return classes.filter(Boolean).join(' ')
}
