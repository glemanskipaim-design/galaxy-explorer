import {
  keepPreviousData,
  QueryClient,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { useEffect } from 'react'
import { CATEGORY_PAGE_SIZE, TIMINGS } from '../config'
import { isTerminal } from '../domain/importStatus'
import {
  createImport,
  getImport,
  getObject,
  listCategories,
  listCategoryObjects,
  searchObjects,
} from './endpoints'
import { ApiRequestError } from './http'
import { qk } from './queryKeys'

/**
 * Repetir so erro de rede/5xx. 4xx nunca: repetir 404 e inutil e, no 429, piora a fila.
 */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiRequestError && error.status < 500) return false
  return failureCount < 2
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  })
}

export function useSearch(q: string) {
  const term = q.trim()
  return useQuery({
    queryKey: qk.search(term),
    queryFn: ({ signal }) => searchObjects(term, signal),
    enabled: term.length > 0,
    placeholderData: keepPreviousData,
    staleTime: TIMINGS.searchStaleMs,
  })
}

export function useObject(slug: string) {
  return useQuery({
    queryKey: qk.object(slug),
    queryFn: ({ signal }) => getObject(slug, signal),
  })
}

export function useCategories() {
  return useQuery({
    queryKey: qk.categories(),
    queryFn: ({ signal }) => listCategories(signal),
    staleTime: TIMINGS.categoriesStaleMs,
  })
}

/**
 * @param page    1-based
 * @param enabled false quando a pagina ja se sabe fora da faixa aceita pelo backend (nem consulta)
 */
export function useCategoryObjects(code: string, page: number, enabled = true) {
  return useQuery({
    enabled,
    queryKey: qk.categoryObjects(code, page),
    queryFn: ({ signal }) =>
      listCategoryObjects(
        code,
        { limit: CATEGORY_PAGE_SIZE, offset: (page - 1) * CATEGORY_PAGE_SIZE },
        signal,
      ),
    placeholderData: keepPreviousData,
  })
}

export function useCreateImport() {
  return useMutation({ mutationFn: (query: string) => createImport(query) })
}

/**
 * Polling do job ate status terminal. `polling=false` para (teto de tempo atingido).
 * Ao concluir com sucesso invalida busca e contagens de categoria (o catalogo mudou).
 */
export function useImportJob(id: number | null, polling: boolean) {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: qk.import(id ?? -1),
    queryFn: ({ signal }) => getImport(id ?? -1, signal),
    enabled: id !== null && polling,
    refetchInterval: (q) => {
      const data = q.state.data
      if (q.state.error || (data && isTerminal(data.status))) return false
      return TIMINGS.importPollMs
    },
  })
  const status = query.data?.status
  useEffect(() => {
    if (status === 'succeeded') {
      void queryClient.invalidateQueries({ queryKey: qk.searchAll() })
      void queryClient.invalidateQueries({ queryKey: qk.categories() })
      void queryClient.invalidateQueries({ queryKey: qk.categoryObjectsAll() })
    }
  }, [status, queryClient])
  return query
}
