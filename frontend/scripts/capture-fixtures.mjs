#!/usr/bin/env node
// Captura respostas REAIS da API local para usar como fixtures dos testes do frontend.
// Nada aqui e digitado a mao: cada arquivo guarda exatamente o corpo que o backend devolveu.
//
// Uso (backend rodando; ver README do frontend):
//   node scripts/capture-fixtures.mjs main       backend normal, seed Messier carregado; exige que
//                                                Betelgeuse ainda NAO esteja no catalogo (exemplo
//                                                de import novo) — num banco onde ja esta, use "basic"
//   node scripts/capture-fixtures.mjs basic      so as capturas sem efeito colateral (busca, 404, 400)
//   node scripts/capture-fixtures.mjs degraded   2a execucao do backend com
//                                                GALAXY_SIMBAD_SESAME_URL=http://127.0.0.1:9/ e
//                                                GALAXY_IMPORTS_QUEUE_CAPACITY=1
//   node scripts/capture-fixtures.mjs coverage   so regenera coverage.md a partir do disco
//
// Alvo: GALAXY_API_TARGET (default http://127.0.0.1:8081).
//
// Efeitos colaterais (esperados, ver risco R16 do plano): a fase "main" importa objetos reais
// do SIMBAD no banco de desenvolvimento (uma consulta ao CDS por termo novo).

import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT = join(ROOT, 'src', 'test', 'fixtures')
const API = (process.env.GALAXY_API_TARGET || 'http://127.0.0.1:8081').replace(/\/$/, '') + '/api/v1'

/** Termo real que (no momento da captura) nao esta no catalogo local: vira o exemplo de import novo. */
const NEW_IMPORT_TERM = 'Betelgeuse'
/** Termo real que JA esta no catalogo: o worker responde "ja existia no catalogo". */
const EXISTING_IMPORT_TERM = 'Vega'
/** Termo inexistente que o Bloco 1 ja usa (Samples.NOT_FOUND_QUERY). */
const NOT_FOUND_TERM = 'XyzzyNotAnObject123'
/** Sgr A*: o slug e lido da resposta, nunca presumido. */
const SGR_TERM = 'Sgr A*'

/** @type {Array<{name: string, phase: string, condition: string}>} */
const captured = []

/**
 * @param {string} method
 * @param {string} path   caminho relativo a /api/v1
 * @param {unknown} [body]
 */
async function call(method, path, body) {
  const res = await fetch(API + path, {
    method,
    headers: body === undefined ? {} : { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  const contentType = res.headers.get('content-type')
  /** @type {unknown} */
  let parsed = text
  if (contentType && contentType.includes('json') && text.length > 0) {
    parsed = JSON.parse(text)
  }
  return {
    request: { method, path: '/api/v1' + path, body: body ?? null },
    status: res.status,
    headers: {
      'content-type': contentType,
      location: res.headers.get('location'),
      'retry-after': res.headers.get('retry-after'),
    },
    capturedAt: new Date().toISOString(),
    body: parsed,
  }
}

/**
 * @param {string} name
 * @param {Awaited<ReturnType<typeof call>>} envelope
 * @param {string} phase
 * @param {string} condition
 */
async function save(name, envelope, phase, condition) {
  await writeFile(join(OUT, name + '.json'), JSON.stringify(envelope, null, 2) + '\n', 'utf8')
  captured.push({ name, phase, condition })
  console.log(`  ${name}  (${envelope.status})`)
  return envelope
}

/** @param {number} ms */
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

/**
 * Faz polling de um job e salva o PRIMEIRO envelope de cada status distinto observado.
 * @param {string} prefix
 * @param {number} id
 * @param {string} phase
 * @param {string} condition
 */
async function pollJob(prefix, id, phase, condition) {
  const seen = new Set()
  const deadline = Date.now() + 180_000
  for (;;) {
    const env = await call('GET', `/imports/${id}`)
    const job = /** @type {{status: string}} */ (env.body)
    if (!seen.has(job.status)) {
      seen.add(job.status)
      await save(`${prefix}-${job.status.replace('_', '-')}`, env, phase, `${condition}; polling GET /imports/${id}`)
    }
    if (!['queued', 'running'].includes(job.status)) return env
    if (Date.now() > deadline) throw new Error(`job ${id} nao terminou em 3 min`)
    await sleep(200)
  }
}

/**
 * POST /imports + polling ate status terminal.
 * @param {string} prefix
 * @param {string} query
 * @param {string} phase
 * @param {string} condition
 */
async function importAndPoll(prefix, query, phase, condition) {
  const created = await save(`${prefix}-created`, await call('POST', '/imports', { query }), phase,
    `${condition}; POST /imports {"query":"${query}"}`)
  if (created.status !== 202) throw new Error(`POST ${query} devolveu ${created.status}`)
  const id = /** @type {{id: number}} */ (created.body).id
  return pollJob(prefix, id, phase, condition)
}

/** Todos os slugs do catalogo, varrendo cada raiz pelo endpoint de categoria. */
async function allSlugs() {
  const cats = /** @type {{categories: {code: string}[]}} */ ((await call('GET', '/categories')).body)
  const slugs = new Set()
  for (const { code } of cats.categories) {
    for (let offset = 0; ; offset += 100) {
      const page = /** @type {{total: number, items: {slug: string}[]}} */ (
        (await call('GET', `/categories/${code}/objects?limit=100&offset=${offset}`)).body)
      page.items.forEach((i) => slugs.add(i.slug))
      if (offset + 100 >= page.total) break
    }
  }
  return [...slugs].sort()
}

// ── Classificacao dos estados de medicao/objeto encontrados nos dados reais ──────────────

/** @typedef {{id:number,value:number|null,errMinus:number|null,errPlus:number|null,limitFlag:string|null,valueText:string|null,unit:string|null,epistemic:string,derivedFrom:number[]|null,reference:unknown}} M */
/** @typedef {{code:string,qualifier:string,available:boolean,absence:{code:string}|null,preferred:M|null,alternatives:M[]}} P */
/** @typedef {{slug:string,category:unknown,existenceStatus:string,position:unknown,properties:P[],sources:{termsUrl:string|null,acknowledgment:string|null}[]}} D */

/**
 * Estados de UI que um objeto exemplifica (chave estavel -> descricao).
 * @param {D} d
 * @returns {Map<string, string>}
 */
function statesOf(d) {
  const s = new Map()
  if (d.position === null) s.set('position-null', 'Objeto sem posição (position: null)')
  if (d.category === null) s.set('category-null', 'Objeto sem categoria (category: null)')
  if (d.existenceStatus !== 'confirmed') s.set(`existence-${d.existenceStatus}`, `existenceStatus = ${d.existenceStatus}`)
  for (const src of d.sources) {
    if (src.termsUrl === null) s.set('source-terms-null', 'Fonte sem termsUrl')
    if (src.acknowledgment === null) s.set('source-ack-null', 'Fonte sem acknowledgment')
  }
  for (const p of d.properties) {
    if (p.absence) s.set(`absence-${p.absence.code}`, `Ausência com código ${p.absence.code}`)
    if (p.qualifier) s.set('qualifier', 'Propriedade com qualificador (ex.: banda fotométrica)')
    if (p.available && p.preferred === null) s.set('available-no-preferred', 'Disponível sem valor preferido (só alternativas)')
    if (p.alternatives.length > 0) s.set('alternatives', 'Propriedade com valores alternativos (alternatives não vazio)')
    for (const m of [p.preferred, ...p.alternatives]) {
      if (!m) continue
      s.set(`epistemic-${m.epistemic}`, `Natureza epistêmica "${m.epistemic}"`)
      if (m.limitFlag) s.set(`limit-${m.limitFlag}`, `limitFlag "${m.limitFlag}"`)
      if (m.valueText !== null) s.set('value-text', 'Valor textual (valueText)')
      if (m.errMinus !== null && m.errPlus !== null && m.errMinus !== m.errPlus)
        s.set('uncertainty-asymmetric', 'Incerteza assimétrica (errMinus ≠ errPlus)')
      if (m.errMinus !== null && m.errMinus === m.errPlus) s.set('uncertainty-symmetric', 'Incerteza simétrica (±)')
      if (m.value !== null && m.errMinus === null && m.errPlus === null) s.set('uncertainty-none', 'Valor numérico sem incerteza')
      if (m.errMinus === null) {
        if (m.errPlus !== null) s.set('uncertainty-one-sided', 'Incerteza só de um lado')
      } else if (m.errPlus === null) s.set('uncertainty-one-sided', 'Incerteza só de um lado')
      if (m.derivedFrom && m.derivedFrom.length > 0) s.set('derived-from', 'Valor calculado com derivedFrom (aponta a medição de origem)')
      if (m.derivedFrom && m.derivedFrom.length > 0 && m.epistemic !== 'computed_by_us')
        s.set('derived-from-by-source', 'derivedFrom em valor que não é computed_by_us')
      if (m.reference === null) s.set('reference-null', 'Medição sem referência bibliográfica')
      if (m.unit === 'arcmin') s.set('unit-arcmin', 'Unidade arcmin')
      if (p.code === 'distance' && m.value !== null && m.value >= 1e6) s.set('distance-mpc', 'Distância ≥ 1 Mpc (escala de milhões de anos-luz)')
      if (p.code === 'distance' && m.value !== null && m.value < 1e3) s.set('distance-pc', 'Distância < 1 kpc (escala em pc)')
    }
  }
  return s
}

/** Todos os estados de UI que o plano quer exemplificar (o que nao tiver exemplo vira gap). */
const EXPECTED_STATES = {
  'epistemic-observed': 'Natureza epistêmica "observed"',
  'epistemic-derived_by_source': 'Natureza epistêmica "derived_by_source"',
  'epistemic-computed_by_us': 'Natureza epistêmica "computed_by_us"',
  'epistemic-estimated': 'Natureza epistêmica "estimated"',
  'epistemic-model': 'Natureza epistêmica "model"',
  'absence-NOT_PROVIDED_BY_SOURCE': 'Ausência com código NOT_PROVIDED_BY_SOURCE',
  'absence-PARALLAX_SNR_BELOW_5': 'Ausência com código PARALLAX_SNR_BELOW_5',
  'absence-PARALLAX_WITHOUT_UNCERTAINTY': 'Ausência com código PARALLAX_WITHOUT_UNCERTAINTY',
  'absence-PARALLAX_UNCERTAINTY_INVALID': 'Ausência com código PARALLAX_UNCERTAINTY_INVALID',
  'absence-PARALLAX_NON_POSITIVE': 'Ausência com código PARALLAX_NON_POSITIVE',
  'limit-<': 'limitFlag "<"',
  'limit->': 'limitFlag ">"',
  'limit-~': 'limitFlag "~"',
  'uncertainty-symmetric': 'Incerteza simétrica (±)',
  'uncertainty-asymmetric': 'Incerteza assimétrica (errMinus ≠ errPlus)',
  'uncertainty-one-sided': 'Incerteza só de um lado',
  'uncertainty-none': 'Valor numérico sem incerteza',
  'value-text': 'Valor textual (valueText)',
  alternatives: 'Propriedade com valores alternativos (alternatives não vazio)',
  'available-no-preferred': 'Disponível sem valor preferido (só alternativas)',
  'derived-from': 'Valor calculado com derivedFrom (aponta a medição de origem)',
  'derived-from-by-source': 'derivedFrom em valor que não é computed_by_us',
  'reference-null': 'Medição sem referência bibliográfica',
  qualifier: 'Propriedade com qualificador (ex.: banda fotométrica)',
  'unit-arcmin': 'Unidade arcmin',
  'distance-pc': 'Distância < 1 kpc (escala em pc)',
  'distance-mpc': 'Distância ≥ 1 Mpc (escala de milhões de anos-luz)',
  'position-null': 'Objeto sem posição (position: null)',
  'category-null': 'Objeto sem categoria (category: null)',
  'existence-not-confirmed': 'existenceStatus diferente de "confirmed"',
  'source-terms-null': 'Fonte sem termsUrl',
  'source-ack-null': 'Fonte sem acknowledgment',
}

/** Estados de fluxo (busca/import/categoria) -> fixture que os exemplifica. */
const FLOW_STATES = [
  ['Busca com hit EXACT', 'search-m31'],
  ['Busca só com hits FUZZY', 'search-fuzzy'],
  ['Busca sem resultado', 'search-empty-betelgeuse'],
  ['Busca 400 (termo longo demais via ?q=)', 'search-400'],
  ['Categorias raiz com contagem', 'categories'],
  ['Listagem de categoria (página 1)', 'category-galaxy-p1'],
  ['Listagem de categoria (página 2)', 'category-galaxy-p2'],
  ['Página de categoria além do total (200, lista vazia)', 'category-galaxy-p3-beyond'],
  ['Categoria 404', 'category-404'],
  ['Objeto 404', 'object-404'],
  ['Import criado (202, queued)', 'import-betelgeuse-created'],
  ['Import em andamento (running)', 'import-betelgeuse-running'],
  ['Import concluído com objeto novo (succeeded, 1 slug)', 'import-betelgeuse-succeeded'],
  ['Import concluído "já existia no catálogo"', 'import-vega-succeeded'],
  ['Import não encontrado (not_found)', 'import-not-found-not-found'],
  ['Import com falha SOURCE_UNAVAILABLE (failed)', 'import-failed-failed'],
  ['Import 400 (charset inválido)', 'import-400'],
  ['Import 404 (job inexistente/expirado)', 'import-404'],
  ['Import 429 (fila cheia, com Retry-After)', 'import-429'],
  ['Import concluído com mais de 1 slug', null],
  ['Import failed "interrompido por reinício do servidor"', null],
  ['Erro 5xx / INTERNAL_ERROR da API', null],
]

async function writeCoverage() {
  const files = (await readdir(OUT)).filter((f) => f.endsWith('.json') && f !== 'manifest.json')
  const present = new Set(files.map((f) => f.replace(/\.json$/, '')))
  /** @type {Map<string, string>} estado -> fixture */
  const examples = new Map()
  for (const f of files.filter((n) => n.startsWith('object-')).sort()) {
    const env = JSON.parse(await readFile(join(OUT, f), 'utf8'))
    if (env.status !== 200) continue
    for (const [key] of statesOf(env.body)) {
      const k = key.startsWith('existence-') ? 'existence-not-confirmed' : key
      if (!examples.has(k)) examples.set(k, f.replace(/\.json$/, ''))
    }
  }
  const lines = [
    '# Cobertura de estados por dados reais',
    '',
    'Gerado por `scripts/capture-fixtures.mjs` a partir das fixtures em disco — não editar à mão.',
    'Estado **sem exemplo real** não ganha teste de componente com dado inventado (plano do Bloco 2,',
    'seção 7.4 / risco R3): só a função pura é testada, quando há valor real citável.',
    '',
    '## Fluxos (busca, importação, categorias)',
    '',
    '| Estado da UI | Fixture |',
    '|---|---|',
    ...FLOW_STATES.map(([label, fx]) =>
      `| ${label} | ${fx && present.has(fx) ? '`' + fx + '`' : '**sem exemplo real**'} |`),
    '',
    '## Página do objeto (varredura de todos os objetos do catálogo local)',
    '',
    '| Estado da UI | Fixture |',
    '|---|---|',
    ...Object.entries(EXPECTED_STATES).map(([key, label]) =>
      `| ${label} | ${examples.has(key) ? '`' + examples.get(key) + '`' : '**sem exemplo real**'} |`),
    '',
  ]
  await writeFile(join(OUT, 'coverage.md'), lines.join('\n'), 'utf8')
  console.log('coverage.md atualizado')
}

async function mergeManifest() {
  const path = join(OUT, 'manifest.json')
  /** @type {{api: string, captures: Array<{name: string, phase: string, condition: string, capturedAt?: string}>}} */
  let manifest = { api: API, captures: [] }
  try {
    manifest = JSON.parse(await readFile(path, 'utf8'))
  } catch {
    // primeiro run
  }
  const names = new Set(captured.map((c) => c.name))
  const capturedAt = new Date().toISOString()
  manifest.captures = [
    ...manifest.captures.filter((c) => !names.has(c.name)),
    ...captured.map((c) => ({ ...c, capturedAt })),
  ].sort((a, b) => a.name.localeCompare(b.name))
  await writeFile(path, JSON.stringify(manifest, null, 2) + '\n', 'utf8')
}

/** Capturas sem efeito colateral (podem ser refeitas a qualquer momento). */
async function phaseBasic() {
  const P = 'main'
  const C = 'backend normal (seed Messier carregado)'
  await save('search-m31', await call('GET', '/search?q=M31'), P, C)
  // "***" e pesquisavel para o normalizador (so espacos/diacriticos somem); o 400 real que a UI
  // pode encontrar e o termo longo demais vindo de ?q= na URL (a caixa limita a 100).
  await save('search-400', await call('GET', '/search?q=' + 'x'.repeat(101)), P, `${C}; q com 101 caracteres`)
  await save('object-404', await call('GET', '/objects/nao-existe'), P, C)
  await save('category-404', await call('GET', '/categories/nao_existe/objects'), P, C)
  await save('category-400', await call('GET', '/categories/galaxy/objects?limit=0'), P, C)
  // pagina 3 das galaxias (offset 48 > total): o backend responde 200 com lista vazia
  await save('category-galaxy-p3-beyond', await call('GET', '/categories/galaxy/objects?limit=24&offset=48'), P,
    `${C}; offset alem do total`)
  await save('import-404', await call('GET', '/imports/999999999'), P, C)
  await save('import-400', await call('POST', '/imports', { query: '<b>x</b>' }), P, C)
}

/** Fluxo completo: importa objetos reais (consulta o CDS) e varre o catalogo. */
async function phaseMain() {
  const P = 'main'
  const C = 'backend normal (seed Messier carregado)'
  await phaseBasic()

  const empty = await call('GET', '/search?q=' + encodeURIComponent(NEW_IMPORT_TERM))
  const emptyHits = /** @type {{hits: unknown[]}} */ (empty.body).hits
  if (emptyHits.length > 0) {
    throw new Error(`${NEW_IMPORT_TERM} ja esta no catalogo; escolha outro termo novo para o exemplo de import`)
  }
  await save('search-empty-betelgeuse', empty, P, `${C}; antes de importar ${NEW_IMPORT_TERM}`)
  const done = await importAndPoll('import-betelgeuse', NEW_IMPORT_TERM, P, `${C}; termo fora do catalogo (consulta real ao CDS)`)
  const slug = /** @type {{resultSlugs: string[]}} */ (done.body).resultSlugs[0]
  await save('object-betelgeuse', await call('GET', `/objects/${slug}`), P, `${C}; apos import de ${NEW_IMPORT_TERM}`)
  await save('search-fuzzy', await call('GET', '/search?q=Betelgeuze'), P, `${C}; grafia errada de ${NEW_IMPORT_TERM}`)

  await importAndPoll('import-vega', EXISTING_IMPORT_TERM, P, `${C}; termo ja presente no catalogo`)
  await save('object-vega', await call('GET', '/objects/vega'), P, C)
  await importAndPoll('import-not-found', NOT_FOUND_TERM, P, `${C}; termo inexistente (consulta real ao Sesame)`)

  // Sgr A*: slug lido da busca (importa antes se nao estiver no catalogo)
  let sgr = await call('GET', '/search?q=' + encodeURIComponent(SGR_TERM))
  if (!/** @type {{hits: {matchType: string}[]}} */ (sgr.body).hits.some((h) => h.matchType === 'EXACT')) {
    await importAndPoll('import-sgr-a', SGR_TERM, P, `${C}; Sgr A* fora do catalogo`)
    sgr = await call('GET', '/search?q=' + encodeURIComponent(SGR_TERM))
  }
  const sgrSlug = /** @type {{hits: {slug: string}[]}} */ (sgr.body).hits[0]?.slug
  if (!sgrSlug) throw new Error('Sgr A* nao encontrado nem apos import')
  await save('object-sgr-a', await call('GET', `/objects/${sgrSlug}`), P, `${C}; slug lido de GET /search?q=Sgr A*`)
  await save('object-messier-31', await call('GET', '/objects/messier-31'), P, C)

  await save('categories', await call('GET', '/categories'), P, `${C}; apos os imports acima`)
  await save('category-galaxy-p1', await call('GET', '/categories/galaxy/objects?limit=24&offset=0'), P, C)
  await save('category-galaxy-p2', await call('GET', '/categories/galaxy/objects?limit=24&offset=24'), P, C)

  // Varredura: o 1o objeto (em ordem de slug) que exemplifica cada estado ainda sem exemplo.
  const already = new Set()
  for (const f of ['object-betelgeuse', 'object-vega', 'object-sgr-a', 'object-messier-31']) {
    const env = JSON.parse(await readFile(join(OUT, f + '.json'), 'utf8'))
    for (const [k] of statesOf(env.body)) already.add(k)
  }
  for (const s of await allSlugs()) {
    const env = await call('GET', `/objects/${s}`)
    const fresh = [...statesOf(/** @type {D} */ (env.body)).keys()].filter((k) => !already.has(k))
    if (fresh.length === 0) continue
    fresh.forEach((k) => already.add(k))
    await save(`object-${s}`, env, P, `${C}; varredura: primeiro exemplo de ${fresh.join(', ')}`)
  }
}

async function phaseDegraded() {
  const P = 'degraded'
  const C = '2a execucao do backend: GALAXY_SIMBAD_SESAME_URL=http://127.0.0.1:9/ (porta sem servico), '
    + 'GALAXY_IMPORTS_QUEUE_CAPACITY=1 — nao toca o CDS'
  // 1o POST: ocupa a unica thread (Sesame morto -> retries -> SOURCE_UNAVAILABLE)
  const first = await save('import-failed-created', await call('POST', '/imports', { query: 'Deneb' }), P, `${C}; POST {"query":"Deneb"}`)
  await sleep(300) // deixa a thread tirar o 1o job da fila
  await call('POST', '/imports', { query: 'Altair' }) // 2o: ocupa a fila (capacidade 1)
  await save('import-429', await call('POST', '/imports', { query: 'Rigel' }), P, `${C}; 3o POST seguido com fila cheia`)
  await pollJob('import-failed', /** @type {{id: number}} */ (first.body).id, P, C)
}

const phase = process.argv[2]
await mkdir(OUT, { recursive: true })
console.log(`API: ${API}  fase: ${phase}`)
if (phase === 'main') await phaseMain()
else if (phase === 'basic') await phaseBasic()
else if (phase === 'degraded') await phaseDegraded()
else if (phase !== 'coverage') {
  console.error('uso: node scripts/capture-fixtures.mjs <main|basic|degraded|coverage>')
  process.exit(2)
}
if (captured.length > 0) await mergeManifest()
await writeCoverage()
