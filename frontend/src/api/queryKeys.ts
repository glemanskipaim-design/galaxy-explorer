/** Fabrica de chaves do TanStack Query (uma fonte so, para invalidar com precisao). */
export const qk = {
  searchAll: () => ['search'] as const,
  search: (q: string) => ['search', q] as const,
  object: (slug: string) => ['object', slug] as const,
  import: (id: number) => ['import', id] as const,
  categories: () => ['categories'] as const,
  categoryObjectsAll: () => ['categoryObjects'] as const,
  categoryObjects: (code: string, page: number) => ['categoryObjects', code, page] as const,
}
