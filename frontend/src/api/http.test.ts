import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, respond } from '../test/handlers'
import { categories, import400, import429, object404 } from '../test/fixtures'
import { server } from '../test/server'
import { createImport, getObject, listCategories } from './endpoints'
import { ApiRequestError } from './http'
import { shouldRetry } from './queries'

describe('requestJson (via endpoints, sob MSW com fixtures reais)', () => {
  it('aceita AbortSignal do ambiente jsdom (risco R10: fetch nativo x AbortSignal)', async () => {
    const controller = new AbortController()
    const res = await listCategories(controller.signal)
    expect(res).toEqual(categories.body)
  })

  it('cancela a requisicao quando o signal e abortado', async () => {
    const controller = new AbortController()
    const pending = listCategories(controller.signal)
    controller.abort()
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('404 do objeto vira ApiRequestError com codigo e mensagem da API', async () => {
    const err: unknown = await getObject('nao-existe').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiRequestError)
    const e = err as ApiRequestError
    expect(e.status).toBe(404)
    expect(e.code).toBe(object404.body.error.code)
    expect(e.message).toBe(object404.body.error.message)
    expect(e.details).toBeNull()
  })

  it('400 do import traz os details reais (campo e mensagem)', async () => {
    server.use(http.post(`${API}/imports`, () => respond(import400)))
    const e = (await createImport('<b>x</b>').catch((x: unknown) => x)) as ApiRequestError
    expect(e.status).toBe(400)
    expect(e.code).toBe('VALIDATION_FAILED')
    expect(e.details).toEqual(import400.body.error.details)
  })

  it('429 le Retry-After (segundos) do header capturado', async () => {
    server.use(http.post(`${API}/imports`, () => respond(import429)))
    const e = (await createImport('Rigel').catch((x: unknown) => x)) as ApiRequestError
    expect(e.status).toBe(429)
    expect(e.code).toBe('IMPORT_QUEUE_FULL')
    expect(e.retryAfterSeconds).toBe(Number(import429.headers['retry-after']))
  })
})

describe('shouldRetry', () => {
  const make = (status: number) =>
    new ApiRequestError('x', { status, code: null, details: null, retryAfterSeconds: null })

  it('nunca repete 4xx (404, 429)', () => {
    expect(shouldRetry(0, make(404))).toBe(false)
    expect(shouldRetry(0, make(429))).toBe(false)
  })

  it('repete 5xx e erro de rede ate 2 vezes', () => {
    expect(shouldRetry(0, make(503))).toBe(true)
    expect(shouldRetry(1, new TypeError('Failed to fetch'))).toBe(true)
    expect(shouldRetry(2, new TypeError('Failed to fetch'))).toBe(false)
  })
})
