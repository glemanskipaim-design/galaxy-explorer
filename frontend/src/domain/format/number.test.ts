import { describe, expect, it } from 'vitest'
import { formatNumber, significantDigitsOf } from './number'

describe('formatNumber', () => {
  it('usa virgula decimal (pt-BR) e sinal de menos tipografico', () => {
    expect(formatNumber(6.55, { fractionDigits: 2 })).toBe('6,55')
    expect(formatNumber(-300, { fractionDigits: 0 })).toBe('−300')
  })

  it('casas fixas completam com zero (mesmo numero de casas da incerteza)', () => {
    expect(formatNumber(13.93, { fractionDigits: 3 })).toBe('13,930')
  })

  it('algarismos significativos', () => {
    expect(formatNumber(0.42, { significantDigits: 2 })).toBe('0,42')
    expect(formatNumber(0.03, { significantDigits: 1 })).toBe('0,03')
  })

  it('notacao cientifica legivel abaixo de 1e-4 e acima de 1e6', () => {
    expect(formatNumber(0.00007308656408078207, { significantDigits: 4 })).toBe('7,309 × 10⁻⁵')
    expect(formatNumber(13930000, { significantDigits: 4 })).toBe('1,393 × 10⁷')
  })

  it('arredondamento que vira 10 reajusta o expoente', () => {
    expect(formatNumber(9.9999e-5, { significantDigits: 2 })).toBe('1,0 × 10⁻⁴')
  })
})

describe('significantDigitsOf', () => {
  it.each([
    ['6.55', 3],
    ['0.373', 3],
    ['761', 3],
    ['-3', 1],
    ['0.03', 1],
    ['7.308656408078207E-5', 16],
    ['-0.0010001920937326991', 17],
  ])('%s -> %i (textos originais reais das fixtures)', (text, expected) => {
    expect(significantDigitsOf(text)).toBe(expected)
  })

  it('texto nao numerico -> null', () => {
    expect(significantDigitsOf('M1-M2Ia-Iab')).toBeNull()
    expect(significantDigitsOf('')).toBeNull()
  })
})
