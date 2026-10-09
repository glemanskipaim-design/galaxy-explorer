import { describe, expect, it } from 'vitest'
import { objectM51 } from '../test/fixtures'
import { imageAlt } from './imageAlt'
import { creditHasOwnLabel } from './imageCredit'
import { imageKindInfo } from './imageKind'
import { safeImageSrc } from './imageSrc'

function heroOf(images: typeof objectM51.body.images) {
  const hero = images[0]
  if (!hero) throw new Error('fixture sem imagem')
  return hero
}

describe('imageKindInfo', () => {
  it.each([
    ['survey_cutout', 'Recorte de survey', 'survey'],
    ['processed_composite', 'Imagem processada', 'photo'],
    ['artist_impression', 'Ilustração artística — não é fotografia', 'warning'],
  ])('%s -> "%s"', (kind, label, variant) => {
    expect(imageKindInfo(kind)).toMatchObject({ label, variant })
  })

  it('tipo desconhecido mostra o codigo cru e nunca finge ser foto', () => {
    expect(imageKindInfo('hologram')).toEqual({
      label: 'hologram',
      description: 'Tipo de imagem não catalogado nesta versão do site.',
      variant: 'unknown',
    })
  })
})

describe('safeImageSrc', () => {
  const hero = heroOf(objectM51.body.images)

  it('aceita o caminho real de arquivo da API (fixture de M51)', () => {
    expect(safeImageSrc(hero.url)).toBe(hero.url)
    expect(safeImageSrc(hero.thumbnail.url)).toBe(hero.thumbnail.url)
  })

  it('recusa URL de terceiro (a pageUrl real), protocolos perigosos, traversal e null', () => {
    expect(safeImageSrc(hero.pageUrl)).toBeNull()
    expect(safeImageSrc('javascript:alert(1)')).toBeNull()
    expect(safeImageSrc('data:image/png;base64,AAAA')).toBeNull()
    expect(safeImageSrc(`/api/v1/images/files/../${hero.url.slice(-68)}`)).toBeNull()
    expect(safeImageSrc(hero.url.toUpperCase())).toBeNull()
    expect(safeImageSrc(null)).toBeNull()
    expect(safeImageSrc(undefined)).toBeNull()
  })
})

describe('imageAlt', () => {
  it('diz o tipo e o provedor, sem descrever conteudo', () => {
    const hero = heroOf(objectM51.body.images)
    expect(imageAlt(objectM51.body.displayName, hero.kind, hero.provider)).toBe(
      `${objectM51.body.displayName} — ${imageKindInfo(hero.kind).label} (${hero.provider})`,
    )
  })
})

describe('creditHasOwnLabel (creditos reais de M51)', () => {
  const credits = objectM51.body.images.map((i) => i.credit)

  it('"Credit: ..." da ESA ja tem rotulo proprio; "NASA, ESA, ..." e "PS1 Science Consortium" nao', () => {
    const labelled = credits.filter((c) => /^Credit:/.test(c))
    const plain = credits.filter((c) => !/^Credit:/.test(c))
    expect(labelled.length).toBeGreaterThan(0)
    expect(plain.length).toBeGreaterThan(0)
    labelled.forEach((c) => expect(creditHasOwnLabel(c)).toBe(true))
    plain.forEach((c) => expect(creditHasOwnLabel(c)).toBe(false))
  })
})
