import { API_BASE } from '../config'
import type { ApiErrorDetail } from './types'

/** Resposta nao-2xx da API, com o que o backend disse (codigo, mensagem pt-BR, detalhes). */
export class ApiRequestError extends Error {
  readonly status: number
  /** codigo do ApiError; null quando o corpo nao segue o formato (ex.: 404/405 padrao do Spring) */
  readonly code: string | null
  readonly details: ApiErrorDetail[] | null
  /** header Retry-After (segundos), presente no 429 */
  readonly retryAfterSeconds: number | null

  constructor(
    message: string,
    init: {
      status: number
      code: string | null
      details: ApiErrorDetail[] | null
      retryAfterSeconds: number | null
    },
  ) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = init.status
    this.code = init.code
    this.details = init.details
    this.retryAfterSeconds = init.retryAfterSeconds
  }
}

function isRecord(x: unknown): x is Record<string, unknown> {
  return typeof x === 'object' && x !== null
}

function parseDetails(raw: unknown): ApiErrorDetail[] | null {
  if (!Array.isArray(raw)) return null
  return raw.filter(isRecord).map((d) => ({
    field: typeof d.field === 'string' ? d.field : '',
    message: typeof d.message === 'string' ? d.message : '',
  }))
}

function parseRetryAfter(raw: string | null): number | null {
  if (raw === null || !/^\d+$/.test(raw.trim())) return null
  return Number(raw.trim())
}

async function toError(res: Response): Promise<ApiRequestError> {
  const retryAfterSeconds = parseRetryAfter(res.headers.get('retry-after'))
  const contentType = res.headers.get('content-type') ?? ''
  if (contentType.includes('json')) {
    try {
      const body: unknown = await res.json()
      if (isRecord(body) && isRecord(body.error)) {
        const e = body.error
        return new ApiRequestError(
          typeof e.message === 'string' ? e.message : `erro ${res.status}`,
          {
            status: res.status,
            code: typeof e.code === 'string' ? e.code : null,
            details: parseDetails(e.details),
            retryAfterSeconds,
          },
        )
      }
    } catch {
      // corpo ilegivel: cai na mensagem generica
    }
  }
  return new ApiRequestError(`O servidor respondeu com erro ${res.status}.`, {
    status: res.status,
    code: null,
    details: null,
    retryAfterSeconds,
  })
}

/**
 * GET/POST JSON na API. `path` e relativo a API_BASE (ex.: `/search?q=M31`).
 * Nao-2xx vira ApiRequestError; falha de rede propaga o erro do fetch.
 */
export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')
  // URL absoluta na MESMA origem da pagina: equivale ao caminho relativo no navegador e
  // tambem funciona no fetch do Node (testes), que nao aceita URL relativa.
  const url = new URL(API_BASE + path, window.location.origin)
  const res = await fetch(url, { ...init, headers })
  if (!res.ok) throw await toError(res)
  return (await res.json()) as T
}
