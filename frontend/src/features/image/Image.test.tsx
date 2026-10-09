import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import indexHtml from '../../../index.html?raw'
import type { ObjectImage } from '../../api/types'
import { creditHasOwnLabel } from '../../domain/imageCredit'
import { imageKindInfo } from '../../domain/imageKind'
import {
  categoryGalaxyP1,
  imageExamples,
  objectM31,
  objectM51,
  objectsBySlug,
  searchM51,
} from '../../test/fixtures'
import { renderApp, renderWithProviders } from '../../test/renderApp'
import { ObjectList } from '../category/ObjectList'
import { ObjectImages } from './ObjectImages'

const FILE_PREFIX = '/api/v1/images/files/'

/** Texto exato do paragrafo de credito: o literal da fonte, com o nosso rotulo so se ela nao rotulou. */
function creditLine(image: ObjectImage): string {
  return creditHasOwnLabel(image.credit) ? image.credit : `Crédito: ${image.credit}`
}

/** Legenda (figcaption) cujo texto contem o credito literal da imagem. */
function captionOf(image: ObjectImage): HTMLElement {
  const credit = screen.getByText((_, el) => el?.tagName === 'P' && el.textContent === creditLine(image))
  const caption = credit.closest('figcaption')
  if (!caption) throw new Error('credito fora de figcaption')
  return caption
}

describe('Pagina do objeto: M51 (fixture real)', () => {
  const images = objectM51.body.images
  const [hero, ...extras] = images

  it('hero com width/height da API (sem layout shift), credito literal, selo, licenca e pagina original', async () => {
    if (!hero) throw new Error('M51 sem imagem na fixture')
    renderApp('/objects/messier-51')
    await screen.findByRole('heading', { level: 1, name: objectM51.body.displayName })

    // a 1a imagem com o alt honesto e o hero (as extras do mesmo provedor tem o mesmo alt)
    const [img] = screen.getAllByRole('img', { name: new RegExp(`^${objectM51.body.displayName} — `) })
    expect(img).toHaveAttribute('src', hero.url)
    expect(img).toHaveAttribute('width', String(hero.width))
    expect(img).toHaveAttribute('height', String(hero.height))
    expect(img).toHaveAttribute('loading', 'eager')

    const caption = captionOf(hero)
    expect(within(caption).getByText(imageKindInfo(hero.kind).label)).toBeInTheDocument()
    expect(within(caption).getByRole('link', { name: new RegExp(hero.license.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')) }))
      .toHaveAttribute('href', hero.license.url)
    expect(caption).toHaveTextContent(hero.provider)
    const original = within(caption).getByRole('link', { name: /Página original|Refazer este recorte no CDS/ })
    expect(original).toHaveAttribute('href', hero.pageUrl)
  })

  it('cada imagem extra aparece com o credito literal completo', async () => {
    renderApp('/objects/messier-51')
    await screen.findByRole('heading', { level: 1, name: objectM51.body.displayName })
    if (extras.length === 0) {
      expect(screen.queryByText(/Outras imagens/)).toBeNull()
      return
    }
    expect(screen.getByRole('heading', { level: 2, name: `Outras imagens (${extras.length})` })).toBeInTheDocument()
    for (const img of extras) expect(captionOf(img)).toBeInTheDocument()
  })
})

describe('Pagina do objeto: M31 (objeto grande)', () => {
  it('mostra primeiro a imagem que a API marcou como hero', async () => {
    const hero = objectM31.body.images[0]
    if (!hero) throw new Error('M31 sem imagem na fixture')
    expect(hero.hero).toBe(true)
    renderApp('/objects/messier-31')
    await screen.findByRole('heading', { level: 1, name: objectM31.body.displayName })
    const imgs = document.querySelectorAll('img:not([aria-hidden])')
    expect(imgs[0]).toHaveAttribute('src', hero.url)
    expect(captionOf(hero)).toBeInTheDocument()
  })
})

describe('Recorte de survey (exemplo real da varredura)', () => {
  it('selo "Recorte de survey", acknowledgment do hips2fits visivel e link para refazer no CDS', async () => {
    const fx = imageExamples.cutoutOnly
    const cut = fx.body.images[0]
    if (!cut) throw new Error('fixture sem recorte')
    renderApp(`/objects/${fx.body.slug}`)
    await screen.findByRole('heading', { level: 1, name: fx.body.displayName })
    const caption = captionOf(cut)
    expect(within(caption).getByText('Recorte de survey')).toBeInTheDocument()
    if (cut.acknowledgment !== null) expect(caption).toHaveTextContent(cut.acknowledgment)
    expect(within(caption).getByRole('link', { name: /Refazer este recorte no CDS/ })).toHaveAttribute('href', cut.pageUrl)
  })
})

describe('Sem imagem (Rigel, NOT_AVAILABLE real: recorte sob demanda falhou)', () => {
  it('pagina mostra "Imagem não disponível" + motivo literal da API, sem nenhum <img>', async () => {
    const fx = imageExamples.absent
    expect(fx.body.images).toHaveLength(0)
    const reason = fx.body.imageAbsence?.message
    if (!reason) throw new Error('fixture sem imageAbsence')
    renderApp(`/objects/${fx.body.slug}`)
    await screen.findByRole('heading', { level: 1, name: fx.body.displayName })
    const notice = screen.getByText('Imagem não disponível').closest('figure')
    if (!notice) throw new Error('aviso fora de figure')
    expect(notice).toHaveTextContent(reason)
    expect(document.querySelectorAll('img')).toHaveLength(0)
  })
})
// NO_POSITION: sem exemplo real no catalogo local — gap em fixtures/coverage.md, sem dado inventado.

describe('ProgressiveImage: falha ao carregar (M51 real)', () => {
  it('erro no <img> vira "Falha ao carregar a imagem" com o alt como nome, e o credito continua visivel', () => {
    const hero = objectM51.body.images[0]
    if (!hero) throw new Error('M51 sem imagem')
    renderWithProviders(<ObjectImages images={[hero]} displayName={objectM51.body.displayName} />)
    const img = document.querySelector(`img[src="${hero.url}"]`)
    if (!img) throw new Error('hero ausente')
    fireEvent.error(img)
    const failed = screen.getByRole('img', { name: new RegExp(`^${objectM51.body.displayName} — `) })
    expect(failed).toHaveTextContent('Falha ao carregar a imagem')
    expect(failed.tagName).not.toBe('IMG')
    expect(document.querySelector(`img[src="${hero.url}"]`)).toBeNull()
    expect(captionOf(hero)).toHaveTextContent(hero.license.name)
  })
})

describe('Nenhuma imagem de fora do site', () => {
  it.each([...objectsBySlug.keys()])('%s: todo img[src] e arquivo da propria API', async (slug) => {
    const fx = objectsBySlug.get(slug)
    if (!fx) throw new Error(slug)
    renderApp(`/objects/${slug}`)
    await screen.findByRole('heading', { level: 1, name: fx.body.displayName })
    for (const img of document.querySelectorAll('img')) {
      expect(img.getAttribute('src')).toMatch(new RegExp(`^${FILE_PREFIX}[0-9a-f]{64}\\.jpg$`))
    }
  })

  it('index.html tem o CSP img-src self (sem data:/blob:)', () => {
    expect(indexHtml).toContain(`<meta http-equiv="Content-Security-Policy" content="img-src 'self'" />`)
  })
})

describe('Miniaturas em busca e listas', () => {
  it('busca M51: miniatura do hero + nome curto do provedor', async () => {
    const hit = searchM51.body.hits[0]
    const thumb = hit?.thumbnail
    if (!hit || !thumb) throw new Error('fixture de busca sem miniatura')
    renderApp('/?q=M51')
    const [link] = await screen.findAllByRole('link', { name: new RegExp(hit.displayName) })
    if (!link) throw new Error('hit ausente')
    const img = link.querySelector('img:not([aria-hidden])')
    expect(img).toHaveAttribute('src', thumb.url)
    expect(img).toHaveAttribute('width', String(thumb.width))
    expect(link).toHaveTextContent(`Imagem: ${thumb.provider} · ${thumb.licenseCode}`)
  })

  it('listagem de categoria: cada item com miniatura ou "sem imagem"', () => {
    const items = categoryGalaxyP1.body.items
    renderWithProviders(<ObjectList items={items} />)
    const links = screen.getAllByRole('link')
    expect(links).toHaveLength(items.length)
    items.forEach((item, i) => {
      const link = links[i]
      if (!link) throw new Error('link ausente')
      if (item.thumbnail === null) expect(link).toHaveTextContent('sem imagem')
      else {
        expect(link.querySelector('img:not([aria-hidden])')).toHaveAttribute('src', item.thumbnail.url)
        expect(link).toHaveTextContent(item.thumbnail.provider)
      }
    })
  })
})
