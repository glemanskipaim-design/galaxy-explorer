import { screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import {
  import404,
  import429,
  importBetelgeuseCreated,
  importBetelgeuseRunning,
  importBetelgeuseSucceeded,
  importFailedFailed,
  importFailedRunning,
  importNotFoundNotFound,
  importNotFoundRunning,
  importSgrARunning,
  importVegaSucceeded,
  search400,
  searchEmpty,
  searchFuzzy,
  searchM31,
} from '../fixtures'
import { API, respond, sequence } from '../handlers'
import { renderApp } from '../renderApp'
import { server } from '../server'

// Tempos curtos nos testes (mockar configuracao nao e mockar dado).
vi.mock('../../config', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../config')>()
  return {
    ...actual,
    TIMINGS: {
      ...actual.TIMINGS,
      searchDebounceMs: 10,
      importPollMs: 150,
      importPollMaxMs: 1200,
      importRedirectDelayMs: 100,
    },
  }
})

const location = () => screen.getByTestId('location').textContent

describe('busca', () => {
  it('hit EXACT (M31): lista o objeto e nao oferece importacao', async () => {
    const { user } = renderApp('/')
    await user.click(screen.getByRole('searchbox'))
    await user.paste('M31')
    const hit = searchM31.body.hits[0]
    if (!hit) throw new Error('fixture vazia')
    const link = await screen.findByRole('link', { name: new RegExp(hit.displayName) })
    expect(link).toHaveAttribute('href', `/objects/${hit.slug}`)
    expect(screen.getByText('1 resultado para “M31”')).toHaveAttribute('role', 'status')
    expect(screen.queryByRole('button', { name: /no SIMBAD e importar/ })).toBeNull()
    expect(location()).toBe('/?q=M31')
  })

  it('so FUZZY (Betelgeuze): selo "aproximado" E oferta de importacao (risco R7)', async () => {
    renderApp('/?q=Betelgeuze')
    expect(await screen.findByText('aproximado')).toBeInTheDocument()
    expect(screen.getByText(searchFuzzy.body.hits[0]?.matchedIdentifier ?? '-')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Buscar “Betelgeuze” no SIMBAD e importar' })).toBeEnabled()
  })

  it('400 da busca mostra a mensagem e o detalhe reais da API', async () => {
    renderApp(`/?q=${'x'.repeat(101)}`)
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(search400.body.error.message)
    expect(alert).toHaveTextContent(search400.body.error.details?.[0]?.message ?? '-')
  })
})

describe('busca -> importacao -> polling', () => {
  it('termo fora do catalogo: oferta, clique, running (Sesame + SIMBAD) e navega para o objeto', async () => {
    server.use(
      http.post(`${API}/imports`, () => respond(importBetelgeuseCreated)),
      http.get(`${API}/imports/:id`, sequence([importBetelgeuseRunning, importBetelgeuseSucceeded])),
    )
    const { user } = renderApp('/')
    await user.click(screen.getByRole('searchbox'))
    await user.paste(searchEmpty.body.query)

    expect(await screen.findByText(/Nenhum resultado no catálogo local/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: `Buscar “${searchEmpty.body.query}” no SIMBAD e importar` }))

    expect(await screen.findByText('Resolvendo o nome no Sesame (CDS) e importando de SIMBAD…')).toBeInTheDocument()
    expect(location()).toContain(`import=${importBetelgeuseCreated.body.id}`)

    const slug = importBetelgeuseSucceeded.body.resultSlugs[0]
    await waitFor(() => expect(location()).toBe(`/objects/${slug}`))
    expect(await screen.findByRole('heading', { level: 1, name: 'Betelgeuse' })).toBeInTheDocument()
  })

  it('"ja existia no catalogo": mostra a mensagem da API antes de navegar (retomado da URL)', async () => {
    server.use(http.get(`${API}/imports/:id`, sequence([importVegaSucceeded])))
    renderApp(`/?import=${importVegaSucceeded.body.id}`)
    expect(await screen.findByText(`Concluído: ${importVegaSucceeded.body.message}.`)).toBeInTheDocument()
    await waitFor(() => expect(location()).toBe(`/objects/${importVegaSucceeded.body.resultSlugs[0]}`))
  })

  it('not_found: mensagem real da API', async () => {
    server.use(http.get(`${API}/imports/:id`, sequence([importNotFoundRunning, importNotFoundNotFound])))
    renderApp(`/?import=${importNotFoundNotFound.body.id}`)
    expect(await screen.findByText(importNotFoundNotFound.body.message ?? '-')).toBeInTheDocument()
  })

  it('failed: mensagem SOURCE_UNAVAILABLE real (Sesame fora do ar na captura)', async () => {
    server.use(http.get(`${API}/imports/:id`, sequence([importFailedRunning, importFailedFailed])))
    renderApp(`/?import=${importFailedFailed.body.id}`)
    expect(await screen.findByText(importFailedFailed.body.message ?? '-')).toBeInTheDocument()
  })

  it('404 do job (expirado/reinicio): mensagem propria', async () => {
    server.use(http.get(`${API}/imports/:id`, () => respond(import404)))
    renderApp('/?import=999999999')
    expect(await screen.findByText('Importação não encontrada (pode ter expirado).')).toBeInTheDocument()
  })

  it('teto do polling: para e avisa (job continua running)', async () => {
    let calls = 0
    server.use(
      http.get(`${API}/imports/:id`, () => {
        calls += 1
        return respond(importSgrARunning)
      }),
    )
    renderApp(`/?import=${importSgrARunning.body.id}`)
    expect(await screen.findByText('Ainda processando — tente novamente mais tarde.', {}, { timeout: 3000 })).toBeInTheDocument()
    const after = calls
    await new Promise((r) => setTimeout(r, 400))
    expect(calls).toBe(after) // parou de consultar
  })

  it('429: mensagem com o Retry-After do header capturado e botao desabilitado', async () => {
    server.use(http.post(`${API}/imports`, () => respond(import429)))
    const { user } = renderApp(`/?q=${searchEmpty.body.query}`)
    const button = await screen.findByRole('button', { name: /no SIMBAD e importar/ })
    await user.click(button)
    const seconds = import429.headers['retry-after']
    expect(await screen.findByText(`Fila de importação cheia. Tente de novo em ${seconds} s.`)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /no SIMBAD e importar/ })).toBeDisabled()
  })
})
