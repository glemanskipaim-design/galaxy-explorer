import { describe, expect, it } from 'vitest'
import type { Measurement } from '../api/types'
import {
  categories,
  importBetelgeuseCreated,
  importBetelgeuseRunning,
  importBetelgeuseSucceeded,
  importFailedFailed,
  importNotFoundNotFound,
  importVegaSucceeded,
  objectBetelgeuse,
  objectM100,
  objectM103,
  objectM31,
} from '../test/fixtures'
import { preferredOf, propertyOf } from '../test/fixtureHelpers'
import { CATEGORY_SHORTCUTS, resolveShortcuts } from './categoryShortcuts'
import { detectConflict } from './conflict'
import { groupProperties, indexMeasurements, propertyLabel } from './displayGroups'
import { epistemicInfo } from './epistemic'
import { importStatusView, isTerminal } from './importStatus'
import { safeUrl } from './safeUrl'

describe('epistemicInfo', () => {
  it.each([
    ['observed', 'Observado', 'observed'],
    ['derived_by_source', 'Derivado pela fonte', 'derived'],
    ['computed_by_us', 'Calculado por nós', 'computed'],
    ['estimated', 'Estimado', 'estimated'],
    ['model', 'Modelo', 'model'],
  ])('%s -> %s', (code, label, variant) => {
    expect(epistemicInfo(code)).toMatchObject({ label, variant })
  })

  it('codigo desconhecido mostra o codigo cru e "natureza nao catalogada"', () => {
    expect(epistemicInfo('simulated')).toEqual({
      label: 'simulated',
      description: 'Natureza não catalogada.',
      variant: 'unknown',
    })
  })
})

describe('detectConflict (Fase 0 §7)', () => {
  it('redshift de M31: SIMBAD (fixture, sem incerteza) vs NED −0,000991 ± 0,000003 (Fase 0 §2 item 4) diverge por 3σ', () => {
    const simbad = preferredOf(objectM31, 'redshift')
    // Valores do NED citados na Fase 0 §2 item 4 (pesquisa ao vivo); o resto do registro e o da
    // fixture so para satisfazer o tipo — o que importa para a regra e valor e incerteza.
    const ned: Measurement = { ...simbad, id: -1, value: -0.000991, errMinus: 0.000003, errPlus: 0.000003, source: 'NED' }
    // |−0,0010002 − (−0,000991)| = 9,2e-6 > 3·3e-6 = 9e-6
    expect(detectConflict([simbad, ned])).toEqual({ diverges: true, rule: '3sigma' })
  })

  it('distancia de M 103: valor calculado por nos diverge das distancias publicadas', () => {
    const p = propertyOf(objectM103, 'distance')
    expect(detectConflict([p.preferred as Measurement, ...p.alternatives])).toEqual({ diverges: true, rule: '3sigma' })
  })

  it('regra de 10% quando nenhum dos dois tem incerteza (distancias publicadas de M100)', () => {
    const alts = propertyOf(objectM100, 'distance').alternatives
    const by = (text: string) => alts.find((m) => m.originalValue === text) as Measurement
    expect(detectConflict([by('15.2'), by('20')])).toEqual({ diverges: true, rule: '10pct' })
    expect(detectConflict([by('15.2'), by('15.21')])).toEqual({ diverges: false, rule: '10pct' })
  })

  it('menos de 2 valores numericos -> insufficient', () => {
    expect(detectConflict([preferredOf(objectBetelgeuse, 'parallax')])).toEqual({
      diverges: false,
      rule: 'insufficient',
    })
    expect(detectConflict([preferredOf(objectBetelgeuse, 'spectral_type'), preferredOf(objectBetelgeuse, 'parallax')]))
      .toEqual({ diverges: false, rule: 'insufficient' })
  })
})

describe('groupProperties', () => {
  it('ordem fixa dos grupos (propriedades reais de M31)', () => {
    expect(groupProperties(objectM31.body.properties).map((g) => g.label)).toEqual([
      'Astrometria',
      'Cinemática',
      'Fotometria',
      'Classificação',
      'Morfologia',
    ])
  })

  it('grupo desconhecido vai ao fim com o codigo cru (nunca some)', () => {
    const [first, ...rest] = objectM31.body.properties
    if (!first) throw new Error('fixture vazia')
    const groups = groupProperties([{ ...first, group: 'grupo_novo' }, ...rest])
    expect(groups.at(-1)).toMatchObject({ group: 'grupo_novo', label: 'grupo_novo' })
  })

  it('rotulo com qualificador', () => {
    expect(propertyLabel(propertyOf(objectBetelgeuse, 'app_mag', 'V'))).toBe('Magnitude aparente (V)')
    expect(propertyLabel(propertyOf(objectBetelgeuse, 'parallax'))).toBe('Paralaxe')
  })

  it('indexMeasurements resolve derivedFrom: distancia de Betelgeuse aponta para a paralaxe', () => {
    const index = indexMeasurements(objectBetelgeuse.body.properties)
    const distance = preferredOf(objectBetelgeuse, 'distance')
    const origin = index.get(distance.derivedFrom?.[0] ?? -1)
    expect(origin?.property.code).toBe('parallax')
    // alternativas tambem entram no indice
    const alt = propertyOf(objectM31, 'distance').alternatives[0]
    expect(alt && indexMeasurements(objectM31.body.properties).get(alt.id)?.measurement).toBe(alt)
  })
})

describe('resolveShortcuts (fixture real de /categories)', () => {
  const resolved = resolveShortcuts(CATEGORY_SHORTCUTS, categories.body.categories)
  const byLabel = (label: string) => resolved.find((s) => s.label === label)

  it('ativo quando a categoria tem objetos, com a contagem real', () => {
    const galaxyCount = categories.body.categories.find((c) => c.code === 'galaxy')?.objectCount
    expect(byLabel('Galáxias')).toEqual({ label: 'Galáxias', to: '/categories/galaxy', count: galaxyCount, disabledReason: null })
    for (const label of ['Estrelas', 'Nebulosas', 'Aglomerados']) {
      expect(byLabel(label)?.to).not.toBeNull()
    }
  })

  it('desabilitado "em breve" sem categoria ou com 0 objetos', () => {
    for (const label of ['Planetas', 'Cometas', 'Asteroides', 'Exoplanetas', 'Buracos Negros']) {
      expect(byLabel(label)).toMatchObject({ to: null, count: null, disabledReason: 'em breve' })
    }
  })

  it('codigo esperado ausente da resposta e divergencia, nao "em breve"', () => {
    // variacao minima da resposta real (secao 7.4: so em funcao pura): como se 'galaxy' fosse renomeada
    const withoutGalaxy = categories.body.categories.filter((c) => c.code !== 'galaxy')
    const r = resolveShortcuts(CATEGORY_SHORTCUTS, withoutGalaxy)
    expect(r.find((s) => s.label === 'Galáxias')).toEqual({
      label: 'Galáxias',
      to: null,
      count: null,
      disabledReason: 'categoria não encontrada no catálogo',
    })
    // categoria que existe com 0 objetos continua "em breve"
    expect(r.find((s) => s.label === 'Exoplanetas')?.disabledReason).toBe('em breve')
  })

  it('resposta indisponivel (erro) -> todos desabilitados "indisponível"', () => {
    expect(resolveShortcuts(CATEGORY_SHORTCUTS, null).every((s) => s.to === null && s.disabledReason === 'indisponível')).toBe(true)
  })

  it('sanidade: todo codigo de atalho existe no backend (pega renome de categoria)', () => {
    const codes = new Set(categories.body.categories.map((c) => c.code))
    for (const s of CATEGORY_SHORTCUTS) {
      if (s.categoryCode !== null) expect(codes, s.categoryCode).toContain(s.categoryCode)
    }
  })
})

describe('importStatusView (cada status capturado)', () => {
  it('queued', () => {
    expect(importStatusView(importBetelgeuseCreated.body)).toEqual({ tone: 'progress', text: 'Na fila de importação…', terminal: false })
  })

  it('running: mensagem unica que nomeia Sesame e SIMBAD (o backend nao expoe a fase)', () => {
    expect(importStatusView(importBetelgeuseRunning.body)).toEqual({
      tone: 'progress',
      text: 'Resolvendo o nome no Sesame (CDS) e importando de SIMBAD…',
      terminal: false,
    })
  })

  it('succeeded com e sem mensagem da API', () => {
    expect(importStatusView(importBetelgeuseSucceeded.body)).toMatchObject({ tone: 'success', terminal: true })
    expect(importStatusView(importVegaSucceeded.body).text).toBe(`Concluído: ${importVegaSucceeded.body.message}.`)
  })

  it('not_found e failed mostram a mensagem da API', () => {
    expect(importStatusView(importNotFoundNotFound.body)).toEqual({
      tone: 'notFound',
      text: importNotFoundNotFound.body.message,
      terminal: true,
    })
    expect(importStatusView(importFailedFailed.body)).toEqual({ tone: 'error', text: importFailedFailed.body.message, terminal: true })
  })

  it('isTerminal', () => {
    expect(['queued', 'running', 'succeeded', 'not_found', 'failed'].map(isTerminal)).toEqual([false, false, true, true, true])
  })
})

describe('safeUrl', () => {
  it('aceita https (adsUrl real, com bibcode encoded)', () => {
    const ads = preferredOf(objectBetelgeuse, 'parallax').reference?.adsUrl ?? null
    expect(safeUrl(ads)).toBe(ads)
  })

  it.each(['javascript:alert(1)', 'http://simbad.cds.unistra.fr/', '/relativo', 'lixo', 'data:text/html,x'])(
    'rejeita %s',
    (raw) => {
      expect(safeUrl(raw)).toBeNull()
    },
  )

  it('null -> null', () => {
    expect(safeUrl(null)).toBeNull()
  })
})
