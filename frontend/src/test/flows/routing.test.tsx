import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { categories, categoryGalaxyP1, categoryGalaxyP2, objectM31 } from '../fixtures'
import { API } from '../handlers'
import { renderApp } from '../renderApp'
import { server } from '../server'

const location = () => screen.getByTestId('location').textContent

describe('rotas', () => {
  it('/objects/messier-31 renderiza o objeto', async () => {
    renderApp('/objects/messier-31')
    expect(await screen.findByRole('heading', { level: 1, name: objectM31.body.displayName })).toBeInTheDocument()
  })

  it('/objects/nao-existe -> mensagem de 404 com link para a busca', async () => {
    renderApp('/objects/nao-existe')
    expect(await screen.findByRole('heading', { name: 'Este objeto não está no catálogo local' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Buscar “nao-existe”' })).toHaveAttribute('href', '/?q=nao-existe')
  })

  it('/categories/galaxy lista a pagina 1 e pagina para a 2', async () => {
    const { user } = renderApp('/categories/galaxy')
    expect(await screen.findByRole('heading', { level: 1, name: categoryGalaxyP1.body.category.namePt })).toBeInTheDocument()
    expect(screen.getByText(`${categoryGalaxyP1.body.total} objetos no catálogo local`)).toBeInTheDocument()
    const first = categoryGalaxyP1.body.items[0]
    if (!first) throw new Error('fixture vazia')
    expect(screen.getByRole('link', { name: new RegExp(`^${first.displayName}`) })).toHaveAttribute('href', `/objects/${first.slug}`)

    const nav = screen.getByRole('navigation', { name: 'Paginação' })
    expect(within(nav).getByText('página 1 de 2')).toBeInTheDocument()
    await user.click(within(nav).getByRole('button', { name: /Próxima/ }))
    const firstP2 = categoryGalaxyP2.body.items[0]
    if (!firstP2) throw new Error('fixture vazia')
    expect(await screen.findByRole('link', { name: new RegExp(`^${firstP2.displayName}`) })).toBeInTheDocument()
    expect(location()).toBe('/categories/galaxy?page=2')
  })

  it('pagina alem do total (resposta real 200 vazia): estado claro com link para a ultima', async () => {
    renderApp('/categories/galaxy?page=3')
    expect(await screen.findByRole('heading', { name: 'Página 3 não existe' })).toBeInTheDocument()
    const pages = Math.ceil(categoryGalaxyP1.body.total / 24)
    expect(screen.getByText(`Esta categoria tem ${pages} páginas.`)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir para a última página' })).toHaveAttribute(
      'href',
      `/categories/galaxy?page=${pages}`,
    )
    expect(screen.queryByText(/página 3 de/)).toBeNull()
  })

  it('pagina alem do offset maximo do backend (10000): nem consulta a API', async () => {
    let calls = 0
    server.use(
      http.get(`${API}/categories/:code/objects`, () => {
        calls += 1
        return HttpResponse.text('nao deveria ser chamado', { status: 599 })
      }),
    )
    renderApp('/categories/galaxy?page=418')
    expect(await screen.findByRole('heading', { name: 'Página 418 não existe' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir para a primeira página' })).toHaveAttribute('href', '/categories/galaxy')
    expect(calls).toBe(0)
  })

  it('/categories/nao_existe -> categoria inexistente', async () => {
    renderApp('/categories/nao_existe')
    expect(await screen.findByRole('heading', { name: 'Categoria inexistente' })).toBeInTheDocument()
  })

  it('rota desconhecida -> NotFoundPage', () => {
    renderApp('/rota/qualquer')
    expect(screen.getByRole('heading', { name: 'Página não encontrada' })).toBeInTheDocument()
  })
})

describe('atalhos de categoria da home', () => {
  it('chip ativo mostra a contagem real e navega', async () => {
    const { user } = renderApp('/')
    const galaxy = categories.body.categories.find((c) => c.code === 'galaxy')
    const chip = await screen.findByRole('link', { name: new RegExp(`Galáxias.*${galaxy?.objectCount ?? -1}`) })
    await user.click(chip)
    expect(location()).toBe('/categories/galaxy')
    expect(await screen.findByRole('heading', { level: 1, name: 'Galáxia' })).toBeInTheDocument()
  })

  it('chip desabilitado nao e link e diz "em breve" em texto', async () => {
    renderApp('/')
    await screen.findByRole('link', { name: /Galáxias/ })
    expect(screen.queryByRole('link', { name: /Buracos Negros/ })).toBeNull()
    const disabled = screen.getByText('Buracos Negros').closest('[aria-disabled="true"]')
    expect(disabled).not.toBeNull()
    expect(disabled).toHaveTextContent('em breve')
  })

  it('falha do endpoint de categorias: todos os atalhos indisponiveis, nenhum link', async () => {
    // Falha de rede (nao e dado inventado: nenhuma resposta de API e fabricada).
    server.use(http.get(`${API}/categories`, () => HttpResponse.error()))
    renderApp('/')
    expect(await screen.findByText('Não foi possível carregar as categorias; os atalhos ficam indisponíveis.')).toBeInTheDocument()
    const section = screen.getByRole('region', { name: 'Explorar por categoria' })
    expect(within(section).queryAllByRole('link')).toHaveLength(0)
    expect(within(section).getAllByText('indisponível')).toHaveLength(9)
  })
})
