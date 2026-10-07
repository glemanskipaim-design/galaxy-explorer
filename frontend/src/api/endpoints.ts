import { requestJson } from './http'
import type {
  CategoryListResponse,
  CategoryObjectsResponse,
  CreateImportRequest,
  ImportJob,
  ObjectDetail,
  SearchResponse,
} from './types'

// Unico lugar que conhece as URLs da API. Todo segmento de caminho passa por encodeURIComponent.

export function searchObjects(q: string, signal?: AbortSignal): Promise<SearchResponse> {
  return requestJson(`/search?q=${encodeURIComponent(q)}`, { signal })
}

export function getObject(slug: string, signal?: AbortSignal): Promise<ObjectDetail> {
  return requestJson(`/objects/${encodeURIComponent(slug)}`, { signal })
}

export function createImport(query: string): Promise<ImportJob> {
  const body: CreateImportRequest = { query }
  return requestJson('/imports', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function getImport(id: number, signal?: AbortSignal): Promise<ImportJob> {
  return requestJson(`/imports/${encodeURIComponent(String(id))}`, { signal })
}

export function listCategories(signal?: AbortSignal): Promise<CategoryListResponse> {
  return requestJson('/categories', { signal })
}

export function listCategoryObjects(
  code: string,
  page: { limit: number; offset: number },
  signal?: AbortSignal,
): Promise<CategoryObjectsResponse> {
  const params = new URLSearchParams({ limit: String(page.limit), offset: String(page.offset) })
  return requestJson(`/categories/${encodeURIComponent(code)}/objects?${params.toString()}`, { signal })
}
