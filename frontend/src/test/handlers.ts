import { http, HttpResponse } from 'msw'
import type { Fixture } from './fixtures'
import {
  categories,
  categoryGalaxyP1,
  categoryGalaxyP2,
  categoryGalaxyP3Beyond,
  category404,
  object404,
  objectsBySlug,
  search400,
  searchEmpty,
  searchFuzzy,
  searchM31,
} from './fixtures'

/** Prefixo do MSW: o fetch relativo do jsdom resolve contra http://localhost:3000 (vitest). */
export const API = '*/api/v1'

/** Resposta HTTP identica a capturada: mesmo status, mesmos headers relevantes, mesmo corpo. */
export function respond<T>(fixture: Fixture<T>): Response {
  const headers = new Headers()
  for (const [name, value] of Object.entries(fixture.headers)) {
    if (value !== null) headers.set(name, value)
  }
  return new Response(JSON.stringify(fixture.body), { status: fixture.status, headers })
}

/**
 * Sequencia para polling: cada chamada devolve a proxima fixture; a ultima se repete.
 * Reproduz a ordem real em que os status foram observados na captura.
 */
export function sequence<T>(fixtures: Fixture<T>[]): () => Response {
  let i = 0
  return () => {
    const f = fixtures[Math.min(i, fixtures.length - 1)]
    i += 1
    if (!f) throw new Error('sequencia vazia')
    return respond(f)
  }
}

/** q da requisicao CAPTURADA -> fixture (vale tambem para o 400, cujo corpo nao traz a query). */
const searchByQuery = new Map<string, Fixture<unknown>>(
  [searchM31, searchFuzzy, searchEmpty, search400].map((f) => [
    new URL(f.request.path, 'http://x').searchParams.get('q') ?? '',
    f,
  ]),
)

/**
 * Handlers padrao: so devolvem fixtures reais. Pedido sem fixture correspondente responde 599,
 * e o `onUnhandledFrame: 'error'` do setup pega rotas nao declaradas — nada de dado inventado.
 */
export const defaultHandlers = [
  http.get(`${API}/search`, ({ request }) => {
    const q = new URL(request.url).searchParams.get('q') ?? ''
    const fixture = searchByQuery.get(q)
    if (!fixture) return HttpResponse.text(`sem fixture para q=${q}`, { status: 599 })
    return respond(fixture)
  }),
  http.get(`${API}/categories`, () => respond(categories)),
  http.get(`${API}/categories/:code/objects`, ({ params, request }) => {
    const offset = new URL(request.url).searchParams.get('offset')
    if (params.code === 'galaxy' && (offset === '0' || offset === null)) return respond(categoryGalaxyP1)
    if (params.code === 'galaxy' && offset === '24') return respond(categoryGalaxyP2)
    if (params.code === 'galaxy' && offset === '48') return respond(categoryGalaxyP3Beyond)
    if (params.code === 'nao_existe') return respond(category404)
    return HttpResponse.text('sem fixture', { status: 599 })
  }),
  http.get(`${API}/objects/:slug`, ({ params }) => {
    const fixture = objectsBySlug.get(String(params.slug))
    if (fixture) return respond(fixture)
    if (params.slug === 'nao-existe') return respond(object404)
    return HttpResponse.text('sem fixture', { status: 599 })
  }),
]
