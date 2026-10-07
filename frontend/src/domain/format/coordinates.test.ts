import { describe, expect, it } from 'vitest'
import { objectM31 } from '../../test/fixtures'
import { formatDec, formatRa } from './coordinates'
import { formatRetrievedAt } from './date'

describe('coordenadas (posicao real de M31)', () => {
  const pos = objectM31.body.position
  if (!pos) throw new Error('fixture de M31 sem posicao')

  it('RA em sexagesimal', () => {
    // 10,684708° / 15 = 0,7123139 h = 00h 42m 44,33s
    expect(formatRa(pos.raDeg)).toBe('00h 42m 44,3s')
  })

  it('Dec em sexagesimal com sinal', () => {
    // 41,26875° = 41° 16′ 07,5″
    expect(formatDec(pos.decDeg)).toBe('+41° 16′ 07,5″')
  })

  it('arredondamento propaga (59,96s -> minuto seguinte)', () => {
    expect(formatRa((59.96 / 3600) * 15)).toBe('00h 01m 00,0s')
    expect(formatDec(-(59.96 / 3600))).toBe('−00° 01′ 00,0″')
  })
})

describe('formatRetrievedAt', () => {
  it('data da coleta em dd/mm/aaaa (UTC)', () => {
    expect(formatRetrievedAt(objectM31.body.position?.retrievedAt ?? '')).toBe('06/10/2026')
  })

  it('texto invalido sai cru', () => {
    expect(formatRetrievedAt('ontem')).toBe('ontem')
  })
})
