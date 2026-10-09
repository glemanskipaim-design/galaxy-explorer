import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { objectBetelgeuse, objectM103, objectM31 } from '../../test/fixtures'
import { preferredOf, propertyOf } from '../../test/fixtureHelpers'
import { renderApp, renderWithProviders } from '../../test/renderApp'
import { ExternalLink } from '../../components/ui/ExternalLink'
import { AbsenceNotice } from './AbsenceNotice'
import { EpistemicBadge } from './EpistemicBadge'

describe('EpistemicBadge', () => {
  it.each([
    ['observed', 'Observado'],
    ['derived_by_source', 'Derivado pela fonte'],
    ['computed_by_us', 'Calculado por nós'],
  ])('%s tem rotulo textual (nao so cor) — codigos reais das fixtures', (code, label) => {
    renderWithProviders(<EpistemicBadge epistemic={code} />)
    expect(screen.getByText(label)).toBeInTheDocument()
  })

  it('codigo desconhecido nao quebra: mostra o codigo e "natureza nao catalogada"', () => {
    renderWithProviders(<EpistemicBadge epistemic="simulated" />)
    expect(screen.getByText('simulated', { exact: false })).toHaveTextContent('simulated (natureza não catalogada)')
  })
})

describe('AbsenceNotice', () => {
  it('ausencia real (paralaxe de M31) -> "Nao disponivel na fonte"', () => {
    const p = propertyOf(objectM31, 'parallax')
    expect(p.available).toBe(false)
    renderWithProviders(<AbsenceNotice absence={p.absence} />)
    expect(screen.getByText('Não disponível na fonte')).toBeInTheDocument()
  })
  it('sem motivo da API (absence null) nao inventa "Nao disponivel na fonte"', () => {
    renderWithProviders(<AbsenceNotice absence={null} />)
    expect(screen.getByText('Indisponível (a API não informou o motivo)')).toBeInTheDocument()
    expect(screen.queryByText('Não disponível na fonte')).toBeNull()
  })
  // PARALLAX_* (mensagem da API + S/N): sem exemplo real no catalogo — ver fixtures/coverage.md.
})

describe('ExternalLink', () => {
  it('https vira link em nova aba com rel seguro', () => {
    const ads = preferredOf(objectBetelgeuse, 'parallax').reference?.adsUrl ?? ''
    renderWithProviders(<ExternalLink href={ads}>ref</ExternalLink>)
    const link = screen.getByRole('link', { name: /ref/ })
    expect(link).toHaveAttribute('href', ads)
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    expect(link).toHaveTextContent('(abre em nova aba)')
  })

  it('protocolo nao-https vira texto puro, sem link', () => {
    renderWithProviders(<ExternalLink href="javascript:alert(1)">perigo</ExternalLink>)
    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.getByText('perigo')).toBeInTheDocument()
  })
})

describe('ObjectPage com a fixture real de M31', () => {
  it('cabecalho, classificacao, cartoes por grupo, link ADS, creditos e imagem real (nao o aviso de ausencia)', async () => {
    renderApp('/objects/messier-31')
    const detail = objectM31.body

    expect(await screen.findByRole('heading', { level: 1, name: detail.displayName })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: detail.category?.namePt })).toHaveAttribute(
      'href',
      `/categories/${detail.category?.code ?? ''}`,
    )

    // "por que esta classificacao?" traz o basis literal
    const explainer = screen.getByText('Por que esta classificação?').closest('details')
    expect(explainer).not.toBeNull()
    expect(explainer).toHaveTextContent(detail.category?.basis ?? '')

    // um cartao por grupo presente + posicao
    for (const name of ['Posição no céu', 'Astrometria', 'Cinemática', 'Fotometria', 'Classificação', 'Morfologia']) {
      expect(screen.getByRole('heading', { level: 2, name })).toBeInTheDocument()
    }

    // link ADS da posicao = adsUrl COMO VEIO da API
    const ref = detail.position?.reference
    if (!ref) throw new Error('fixture sem referencia de posicao')
    const posCard = screen.getByRole('region', { name: 'Posição no céu' })
    expect(within(posCard).getByRole('link', { name: new RegExp(ref.bibcode.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')) }))
      .toHaveAttribute('href', ref.adsUrl)
    expect(within(posCard).getByText('00h 42m 44,3s')).toBeInTheDocument()

    // creditos com o acknowledgment literal
    const sources = screen.getByRole('region', { name: 'Fontes e créditos' })
    const ack = detail.sources[0]?.acknowledgment ?? ''
    expect(within(sources).getByText((_, el) => el?.tagName === 'BLOCKQUOTE' && el.textContent === ack)).toBeInTheDocument()

    expect(detail.images.length).toBeGreaterThan(0)
    expect(screen.queryByText('Imagem não disponível')).toBeNull()
  })

  it('distancia de M31: 12 alternativas publicadas e selo de divergencia', async () => {
    renderApp('/objects/messier-31')
    const row = (await screen.findByRole('heading', { level: 1 })) && document.getElementById('prop-distance-x')
    if (!row) throw new Error('linha de distancia ausente')
    const n = propertyOf(objectM31, 'distance').alternatives.length
    expect(within(row).getByText(`Outros valores publicados (${n})`)).toBeInTheDocument()
    expect(within(row).getByText(/Fontes divergem/)).toBeInTheDocument()
    // valor preferido: 761 ± 11 kpc (e o mesmo em milhoes de anos-luz)
    const preferred = document.getElementById(`m-${propertyOf(objectM31, 'distance').preferred?.id ?? -1}`)
    if (!preferred) throw new Error('medicao preferida ausente')
    expect(preferred.querySelector('span')).toHaveTextContent('761 ± 11 kpc')
    expect(preferred).toHaveTextContent('2,482 ± 0,036 milhões de anos-luz')
  })

  it('Betelgeuse: distancia calculada por nos aponta para a paralaxe de origem', async () => {
    renderApp('/objects/betelgeuse')
    await screen.findByRole('heading', { level: 1, name: 'Betelgeuse' })
    const row = document.getElementById('prop-distance-x')
    if (!row) throw new Error('linha de distancia ausente')
    expect(within(row).getByText('Calculado por nós')).toBeInTheDocument()
    expect(within(row).getByText(/calculado por nós a partir de/)).toBeInTheDocument()
    expect(within(row).getByRole('link', { name: 'Paralaxe' })).toHaveAttribute('href', '#prop-parallax-x')
    expect(within(row).getByText('1/plx')).toBeInTheDocument()
    expect(within(row).getByText('+22 / −17')).toBeInTheDocument()
    expect(within(row).getByText('498')).toBeInTheDocument()
  })

  it('valor sem derivedFrom e sem metodo (paralaxe real de Betelgeuse) nao gera paragrafo vazio', async () => {
    renderApp('/objects/betelgeuse')
    await screen.findByRole('heading', { level: 1, name: 'Betelgeuse' })
    const parallax = preferredOf(objectBetelgeuse, 'parallax')
    expect(parallax.method).toBeNull()
    expect(parallax.derivedFrom ?? []).toHaveLength(0)
    const m = document.getElementById(`m-${parallax.id}`)
    if (!m) throw new Error('medicao ausente')
    const paragraphs = [...m.querySelectorAll('p')]
    expect(paragraphs.every((p) => (p.textContent ?? '').trim() !== '')).toBe(true)
    expect(m).not.toHaveTextContent(/a partir de|derivado de|método/)
  })

  it('M 103: medicao real sem referencia bibliografica diz isso', async () => {
    renderApp('/objects/messier-103')
    await screen.findByRole('heading', { level: 1, name: objectM103.body.displayName })
    const row = document.getElementById('prop-app_mag-V')
    if (!row) throw new Error('linha da magnitude V ausente')
    expect(within(row).getByText('sem referência bibliográfica na fonte')).toBeInTheDocument()
  })
})
