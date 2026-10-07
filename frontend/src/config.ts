/** Prefixo de toda chamada a API. Caminho relativo: o navegador so fala com a origem do Vite (proxy). */
export const API_BASE = '/api/v1'

/** Tempos da UI. Testes de fluxo sobrescrevem com valores pequenos (mockar configuracao nao e mockar dado). */
export const TIMINGS = {
  /** espera depois da ultima tecla antes de buscar */
  searchDebounceMs: 300,
  /** intervalo de polling do job de importacao */
  importPollMs: 1000,
  /** teto do polling: depois disso a UI para e avisa (o servidor pode ter caido) */
  importPollMaxMs: 180_000,
  /** quanto tempo a mensagem "ja existia no catalogo" fica visivel antes de navegar */
  importRedirectDelayMs: 1500,
  /** resultado de busca considerado fresco */
  searchStaleMs: 30_000,
  /** lista de categorias considerada fresca */
  categoriesStaleMs: 5 * 60_000,
}

/** Itens por pagina na listagem de categoria. */
export const CATEGORY_PAGE_SIZE = 24

/** Espelho do `@Max(10000)` de `offset` no CategoryController do backend. */
export const CATEGORY_MAX_OFFSET = 10_000

/** Limite do SearchController do backend. */
export const SEARCH_MAX_LENGTH = 100
