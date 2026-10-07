import { describe, expect, it } from 'vitest'
import { objectBetelgeuse, objectM100, objectM31 } from '../../test/fixtures'
import { preferredOf } from '../../test/fixtureHelpers'
import { formatMeasurement, limitPrefix } from './measurement'
import { decimalsForUncertainty, displayDecimals, formatUncertainty } from './uncertainty'
import { unitSymbol, withUnit } from './units'

describe('formatMeasurement (valores reais das fixtures)', () => {
  it('paralaxe de Betelgeuse: valor e ± com o mesmo numero de casas, virgula decimal', () => {
    // SIMBAD: plx 6.55 ± 0.83 mas
    expect(formatMeasurement(preferredOf(objectBetelgeuse, 'parallax'))).toEqual({
      value: '6,55',
      uncertainty: '± 0,83',
      unit: 'mas',
      limit: null,
    })
  })

  it('velocidade radial de M31: −300 ± 4 km/s', () => {
    expect(formatMeasurement(preferredOf(objectM31, 'radial_velocity'))).toEqual({
      value: '−300',
      uncertainty: '± 4',
      unit: 'km/s',
      limit: null,
    })
  })

  it('magnitude sem incerteza mantem os algarismos da fonte (0.42 nao vira 0,4200)', () => {
    expect(formatMeasurement(preferredOf(objectBetelgeuse, 'app_mag', 'V')).value).toBe('0,42')
  })

  it('tipo espectral (valueText) sai exatamente como veio', () => {
    const m = preferredOf(objectBetelgeuse, 'spectral_type')
    expect(formatMeasurement(m)).toMatchObject({ value: m.valueText, uncertainty: null, unit: '' })
  })

  it('tamanho angular em arcmin usa o simbolo ′ colado ao numero', () => {
    const f = formatMeasurement(preferredOf(objectM100, 'ang_size_major'))
    expect(f.unit).toBe('′')
    expect(withUnit(f.value, f.unit)).toBe('6,30534′')
  })

  it('redshift sem incerteza com 16+ algarismos de ponto flutuante: teto de 7 significativos', () => {
    // texto original da fonte: 7.308656408078207E-5 (fica integral em "detalhes")
    expect(formatMeasurement(preferredOf(objectBetelgeuse, 'redshift')).value).toBe('7,308656 × 10⁻⁵')
  })
})

describe('uncertainty', () => {
  it('2 algarismos significativos da menor incerteza', () => {
    expect(decimalsForUncertainty(0.83, 0.83)).toBe(2)
    expect(decimalsForUncertainty(17.17, 22.15)).toBe(0)
    expect(decimalsForUncertainty(11000, 11000)).toBe(0)
    expect(decimalsForUncertainty(null, null)).toBeNull()
  })

  it('nunca mais casas do que o valor publicado; piso de 1 significativo da incerteza', () => {
    expect(displayDecimals(4, 4, 0)).toBe(0) // M31 rv: "-300" ± 4
    expect(displayDecimals(0.83, 0.83, 2)).toBe(2) // Betelgeuse plx: "6.55" ± 0.83
    expect(displayDecimals(0.06, 0.06, 2)).toBe(2) // M100: "13.93" ± 0.06 Mpc
    expect(displayDecimals(0.0008, 0.0008, 4)).toBe(4) // M100 g: "10.2838" ± 0.0008
    expect(displayDecimals(0.004, 0.004, 0)).toBe(3) // piso: o ± nunca vira 0
    expect(displayDecimals(0.83, 0.83, null)).toBe(2)
  })

  it('assimetrica vira +a / −b (incerteza real da distancia calculada de Betelgeuse)', () => {
    const d = preferredOf(objectBetelgeuse, 'distance')
    expect(formatUncertainty(d.errMinus, d.errPlus, 0)).toBe('+22 / −17')
  })

  it('ambos null -> null', () => {
    expect(formatUncertainty(null, null, 2)).toBeNull()
  })
})

describe('limitPrefix e unitSymbol', () => {
  // Nenhuma fixture real tem limitFlag (ver coverage.md): so a funcao pura e testada.
  it('mapeia < > ~', () => {
    expect(limitPrefix('<')).toBe('<')
    expect(limitPrefix('>')).toBe('>')
    expect(limitPrefix('~')).toBe('≈')
    expect(limitPrefix(null)).toBeNull()
  })

  it('unidade desconhecida aparece crua; null vira vazio', () => {
    expect(unitSymbol('mas')).toBe('mas')
    expect(unitSymbol('erg/s')).toBe('erg/s')
    expect(unitSymbol(null)).toBe('')
  })
})
