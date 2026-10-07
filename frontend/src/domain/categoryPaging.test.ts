import { describe, expect, it } from 'vitest'
import { categoryGalaxyP1 } from '../test/fixtures'
import { maxRequestablePage, pageCountOf, parsePage } from './categoryPaging'

describe('categoryPaging', () => {
  it('parsePage: ausente, lixo e zero viram 1', () => {
    expect(parsePage(null)).toBe(1)
    expect(parsePage('')).toBe(1)
    expect(parsePage('abc')).toBe(1)
    expect(parsePage('0')).toBe(1)
    expect(parsePage('-2')).toBe(1)
    expect(parsePage('3')).toBe(3)
    expect(parsePage('9999999')).toBe(9999999)
  })

  it('maior pagina aceita pelo backend: offset <= 10000 com 24 por pagina -> 417', () => {
    expect(maxRequestablePage(24, 10_000)).toBe(417)
    expect((417 - 1) * 24).toBeLessThanOrEqual(10_000)
    expect((418 - 1) * 24).toBeGreaterThan(10_000)
  })

  it('total de paginas (total real das galaxias)', () => {
    expect(pageCountOf(categoryGalaxyP1.body.total, 24)).toBe(Math.ceil(categoryGalaxyP1.body.total / 24))
    expect(pageCountOf(0, 24)).toBe(1)
  })
})
