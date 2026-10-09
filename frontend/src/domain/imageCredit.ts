/** A fonte ja rotulou o proprio texto ("Credit:", "Image credit:", "Créditos:")? */
const OWN_LABEL = /^\s*(image\s+)?(credits?|cr[eé]ditos?)\s*:/i

/**
 * Decide so se o NOSSO rotulo "Crédito:" aparece (evita "Crédito: Credit: ..."). O texto da fonte
 * nunca e cortado nem reescrito.
 */
export function creditHasOwnLabel(credit: string): boolean {
  return OWN_LABEL.test(credit)
}
