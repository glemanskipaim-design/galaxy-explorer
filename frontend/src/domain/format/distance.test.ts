import { describe, expect, it } from 'vitest'
import { objectBetelgeuse, objectM100, objectM31, objectVega } from '../../test/fixtures'
import { preferredOf } from '../../test/fixtureHelpers'
import { formatDistance, LY_PER_PC } from './distance'

describe('LY_PER_PC', () => {
  it('bate com a derivacao IAU: (648000/π)·au / (c·ano juliano) = 3,261563777 ano-luz', () => {
    expect(Math.abs(LY_PER_PC - 3.261563777)).toBeLessThan(1e-9)
  })
})

describe('formatDistance (distancias reais das fixtures)', () => {
  it('Betelgeuse: distancia calculada por nos a partir da paralaxe, incerteza assimetrica', () => {
    const d = preferredOf(objectBetelgeuse, 'distance')
    expect(d.epistemic).toBe('computed_by_us')
    expect(formatDistance(d)).toEqual({
      pc: { value: '153', uncertainty: '+22 / −17', unit: 'pc' },
      ly: { value: '498', uncertainty: '+72 / −56', unit: 'anos-luz' },
    })
  })

  it('Vega: incertezas quase iguais arredondam para o mesmo ±', () => {
    expect(formatDistance(preferredOf(objectVega, 'distance'))).toEqual({
      pc: { value: '7,679', uncertainty: '± 0,021', unit: 'pc' },
      ly: { value: '25,045', uncertainty: '± 0,069', unit: 'anos-luz' },
    })
  })

  it('M31: 761 ± 11 kpc em kpc e em milhoes de anos-luz', () => {
    expect(formatDistance(preferredOf(objectM31, 'distance'))).toEqual({
      pc: { value: '761', uncertainty: '± 11', unit: 'kpc' },
      ly: { value: '2,482', uncertainty: '± 0,036', unit: 'milhões de anos-luz' },
    })
  })

  it('M100: escala Mpc / milhoes de anos-luz; nunca mais casas que a fonte (13.93 Mpc)', () => {
    expect(formatDistance(preferredOf(objectM100, 'distance'))).toEqual({
      pc: { value: '13,93', uncertainty: '± 0,06', unit: 'Mpc' },
      ly: { value: '45,43', uncertainty: '± 0,20', unit: 'milhões de anos-luz' },
    })
  })

  it('unidade canonica diferente de pc -> null (nao assume unidade; cai no formatMeasurement)', () => {
    // variacao minima de um campo de medicao real (secao 7.4: so em funcao pura)
    expect(formatDistance({ ...preferredOf(objectM31, 'distance'), unit: 'kpc' })).toBeNull()
    expect(formatDistance({ ...preferredOf(objectM31, 'distance'), unit: null })).toBeNull()
  })

  it('sem incerteza: algarismos significativos da fonte (15.2 Mpc -> 3)', () => {
    const alt = objectM100.body.properties.find((p) => p.code === 'distance')?.alternatives.find(
      (m) => m.originalValue === '15.2',
    )
    if (!alt) throw new Error('fixture mudou')
    expect(formatDistance(alt)?.pc).toEqual({ value: '15,2', uncertainty: null, unit: 'Mpc' })
    expect(formatDistance(alt)?.ly).toEqual({ value: '49,6', uncertainty: null, unit: 'milhões de anos-luz' })
  })
})
