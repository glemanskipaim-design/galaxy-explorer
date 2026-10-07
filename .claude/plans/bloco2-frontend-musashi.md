---
agent: musashi
timestamp: 2026-10-06   # data do ambiente; hora UTC nao obtida (musashi nao tem Bash)
target_files:
  - backend/src/main/resources/db/migration/V5__natural_sort_collation.sql
  - backend/src/main/java/dev/galaxyexplorer/catalog/category/CategoryRepository.java (+2 metodos)
  - backend/src/main/java/dev/galaxyexplorer/catalog/category/CategoryCount.java (novo)
  - backend/src/main/java/dev/galaxyexplorer/catalog/object/CelestialObjectRepository.java (+2 metodos)
  - backend/src/main/java/dev/galaxyexplorer/catalog/read/CategoryQueryService.java (novo)
  - backend/src/main/java/dev/galaxyexplorer/catalog/read/CategoryPage.java (novo)
  - backend/src/main/java/dev/galaxyexplorer/api/category/CategoryController.java (novo)
  - backend/src/main/java/dev/galaxyexplorer/api/category/CategoryListResponse.java (novo)
  - backend/src/main/java/dev/galaxyexplorer/api/category/CategoryObjectsResponse.java (novo)
  - backend/src/main/java/dev/galaxyexplorer/api/error/ApiErrorCode.java (+CATEGORY_NOT_FOUND)
  - backend/src/test/java/dev/galaxyexplorer/api/CategoryApiIntegrationTest.java (novo)
  - frontend/** (projeto novo — arvore completa abaixo)
  - .claude/qa-gate.config.json
  - .gitignore (+frontend/coverage/)
  - docker-compose.yml (sem mudanca — decisao: adiar; ver secao 9)
---

# Bloco 2 — Frontend: busca + página de detalhe

Fontes de verdade: `docs/00-fase0-arquitetura.md` (aprovado), o código da API
do Bloco 1 (commit 4ee0db7) e as recomendações de frontend do
`.claude/review-log/20261006T154659Z-yagyu.md` (linhas 102-108). Este plano
não reabre decisões da Fase 0. Onde preenchi lacuna, está marcado
**[DECISÃO NOVA]** com o motivo, para o usuário poder vetar antes da
implementação.

Convenções gerais para o `sasaki`:

- **Leia este arquivo, não o resumo repassado na conversa.**
- Toda versão de pacote é **verificada no passo 0** (`npm view`). As versões
  que eu conheço podem estar desatualizadas — não presuma nenhuma.
- TypeScript `strict` sem exceção; nenhum `any` explícito (ESLint bloqueia).
- Todo texto que vem da API (nomes, identificadores, `job.message`,
  `category.basis`, acknowledgments) é renderizado **como texto React**,
  nunca `dangerouslySetInnerHTML` — e isso é **forçado por lint** (seção 8), não
  só combinado.
- Toda URL externa passa por um único componente `ExternalLink` (seção 4.6).
- Dados de teste = **respostas reais capturadas da API local**. Nenhum JSON
  digitado à mão. Quando um estado da UI não tem exemplo real capturável, o
  teste daquele estado **não é escrito com dado inventado** — o gap é
  registrado (seção 7.4).

---

## Estrutura proposta

### 0. Visão geral (didática)

```
 navegador ──► Vite dev server (127.0.0.1:5173)
                 │  /            → app React (HMR)
                 │  /api/**      → proxy ──► backend Spring (127.0.0.1:8081)
                 ▼
          React Router  ──► páginas (Home, Objeto, Categoria, 404)
                 │
          TanStack Query ──► api/ (fetch tipado) ──► /api/v1/...
                 │
          domain/ (funções puras: formatação, selos, conflito, distância)
```

Direção de dependência no frontend — a regra mais importante:

```
pages ──► features ──► components/ui
  │          │
  └──────────┴──► api (tipos + fetch + hooks de query)
             └──► domain (funções puras, sem React, sem fetch)
```

- `domain/` não importa React nem `api/http`. Só tipos de `api/types.ts`.
  Isso faz toda a lógica "científica" de exibição (incerteza, ano-luz, selo
  epistêmico, conflito) ser testável como função pura, rápida, sem DOM.
- `api/` é o único lugar que conhece URLs e `fetch`. Se um endpoint mudar,
  muda só ali.
- `features/` agrupa componentes por funcionalidade (busca, objeto,
  categoria, home), não por tipo técnico. Quem mexe na página de objeto
  encontra tudo numa pasta.
- `components/ui/` são primitivas genéricas sem conhecimento de astronomia.

### 1. Backend — adição mínima (permitida neste bloco)

**Por que é necessária:** hoje não existe endpoint que diga "quantos objetos
há por categoria" nem "liste os objetos da categoria X". Sem isso, o
frontend teria de **adivinhar** quais atalhos ativar (hardcode de
"galáxias tem dados") — exatamente o "fingir dados" que a especificação
proíbe. Com o endpoint, o atalho fica ativo se e somente se o banco tem
objetos daquela categoria.

Dois endpoints, mesmo controller:

| Endpoint | Para quê |
|---|---|
| `GET /api/v1/categories` | Categorias **raiz** com contagem de objetos (incluindo subcategorias). Alimenta os atalhos da home. |
| `GET /api/v1/categories/{code}/objects?limit&offset` | Lista paginada dos objetos da subárvore de `{code}`. Destino de um atalho ativo. |

Arquivos (seguindo o padrão já existente: repositório no pacote de domínio,
serviço de leitura em `catalog/read`, DTO de contrato em `api/<recurso>`):

| Arquivo | Responsabilidade única |
|---|---|
| `catalog/category/CategoryCount.java` (record) | Uma categoria raiz + `objectCount`. |
| `catalog/category/CategoryRepository.java` (+2 métodos) | `findRootsWithCounts()` e `findSubtreeIds(int categoryId)`. **Toda** consulta que percorre a hierarquia `object_category.parent_id` fica nesta classe (CTE recursiva), em um lugar só. |
| `catalog/object/CelestialObjectRepository.java` (+2 métodos) | `findByCategoryIds(Collection<Integer> ids, int limit, int offset)` e `countByCategoryIds(Collection<Integer> ids)`. Não sabe nada de hierarquia — recebe ids já resolvidos. |
| `catalog/read/CategoryPage.java` (record) | Read model: categoria + total + itens (`ObjectRow` + nome da categoria do item). |
| `catalog/read/CategoryQueryService.java` | Orquestra: `roots()` e `objectsOf(code, limit, offset)`. |
| `api/category/CategoryController.java` | HTTP, validação de `code`/`limit`/`offset`, 404. |
| `api/category/CategoryListResponse.java`, `CategoryObjectsResponse.java` | Contrato JSON (records aninhados, no estilo de `ObjectDetailResponse`). |
| `api/error/ApiErrorCode.java` | + `CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND)`. |
| `db/migration/V5__natural_sort_collation.sql` | Cria collation ICU numérica (ver abaixo). |
| `test/.../api/CategoryApiIntegrationTest.java` | Testes (seção 7.1). |

**[DECISÃO NOVA] Ordenação natural (V5).** Ordenar por `display_name` puro
dá `M 1, M 10, M 100, M 101, …, M 2` — feio justamente no catálogo Messier,
que é a vitrine. A forma nativa do PostgreSQL é uma collation ICU com
ordenação numérica (`provider = icu`, locale `und-u-kn-true`), usada como
`ORDER BY display_name COLLATE natural_sort, slug`. A imagem oficial
`postgres:17` (docker-compose e Testcontainers) é compilada com ICU.
Alternativa descartada: ordenar em memória no Java (quebra a paginação no
SQL). Fallback se a collation falhar no ambiente: `ORDER BY display_name,
slug` e registrar a limitação — não inventar regex de ordenação.

**Não reaproveitar `SearchHitDto`** para os itens da listagem: ele tem
`matchedIdentifier`/`matchType`/`score`, que não fazem sentido aqui.
Reaproveitar com campos `null` enfraqueceria o contrato da busca.

### 2. Frontend — árvore de arquivos

```
frontend/
├── package.json                 scripts: dev, build, preview, test, test:watch, lint, typecheck, capture-fixtures
├── package-lock.json            commitado; instalar com `npm ci`
├── index.html                   lang="pt-BR", viewport, theme-color escuro
├── vite.config.ts               plugin React, proxy /api, bloco `test` do Vitest
├── tsconfig.json                só "references" (padrão do template Vite)
├── tsconfig.app.json            strict + extras (seção 2.1), resolveJsonModule
├── tsconfig.node.json           para vite.config.ts e scripts/
├── eslint.config.js             flat config (seção 8)
├── .env.example                 documenta GALAXY_API_TARGET
├── README.md                    como rodar, como recapturar fixtures
├── scripts/
│   └── capture-fixtures.mjs     captura respostas reais da API local (seção 7.3)
├── public/
│   └── favicon.svg
└── src/
    ├── main.tsx                 monta providers (QueryClient, BrowserRouter) + importa fontes e CSS global
    ├── App.tsx                  AppShell + <Routes> (seção 3)
    ├── config.ts                TIMINGS e API_BASE (constantes; mockável em teste)
    ├── vite-env.d.ts
    ├── styles/
    │   ├── tokens.css           CSS custom properties (seção 5.2)
    │   └── global.css           reset, base, foco, reduced-motion, fundo
    ├── api/
    │   ├── types.ts             espelho dos DTOs Java (seção "Interfaces")
    │   ├── http.ts              fetch JSON + ApiRequestError
    │   ├── endpoints.ts         uma função por endpoint
    │   ├── queryKeys.ts         fábrica de chaves do TanStack Query
    │   └── queries.ts           hooks useSearch, useObject, useCategories, ...
    ├── domain/                  PURO: sem React, sem fetch
    │   ├── format/
    │   │   ├── number.ts        Intl pt-BR, algarismos significativos
    │   │   ├── uncertainty.ts   ± simétrico / +a −b assimétrico, limit flags
    │   │   ├── units.ts         código de unidade → símbolo exibido
    │   │   ├── measurement.ts   compõe valor + unidade + incerteza
    │   │   ├── distance.ts      pc ↔ anos-luz (constante citada) + escala
    │   │   ├── coordinates.ts   RA/Dec graus → sexagesimal
    │   │   └── date.ts          retrievedAt → data pt-BR
    │   ├── epistemic.ts         código → rótulo, descrição, variante visual
    │   ├── displayGroups.ts     display_group → rótulo + ordem; agrupamento
    │   ├── conflict.ts          regra 3σ / 10% da Fase 0 §7
    │   ├── categoryShortcuts.ts atalhos da home → código de categoria
    │   ├── importStatus.ts      status do job → mensagem + terminal?
    │   └── safeUrl.ts           só aceita https:
    ├── components/
    │   ├── layout/
    │   │   ├── AppShell.tsx (+ .module.css)
    │   │   └── SkipLink.tsx
    │   └── ui/
    │       ├── VisuallyHidden.tsx
    │       ├── LoadingState.tsx
    │       ├── ErrorState.tsx
    │       ├── ExternalLink.tsx
    │       └── *.module.css
    ├── features/
    │   ├── home/       Hero, CategoryShortcuts, CategoryChip
    │   ├── search/     SearchBox, SearchResults, SearchResultItem, ImportOffer, ImportProgress, useDebouncedValue.ts
    │   ├── object/     ObjectHeader, ClassificationExplainer, IdentifierList, ImagePlaceholder,
    │   │               PositionCard, PropertyGroupCard, PropertyRow, MeasurementValue,
    │   │               EpistemicBadge, ProvenanceLine, AbsenceNotice, AlternativeValues,
    │   │               ConflictBadge, SourcesSection
    │   └── category/   ObjectList, Pagination
    ├── pages/
    │   ├── HomePage.tsx
    │   ├── ObjectPage.tsx
    │   ├── CategoryPage.tsx
    │   └── NotFoundPage.tsx
    └── test/
        ├── setup.ts            jest-dom + ciclo de vida do MSW (onUnhandledRequest: 'error')
        ├── server.ts           setupServer do MSW
        ├── handlers.ts         fixture → handler; helper de sequência para polling
        ├── renderApp.tsx       MemoryRouter + QueryClient novo por teste (retry: false)
        ├── flows/              testes de fluxo (busca→import→polling, roteamento)
        └── fixtures/
            ├── manifest.json   o que foi capturado, como, quando
            ├── coverage.md     quais estados têm exemplo real e quais não
            ├── index.ts        exports TIPADOS das fixtures (contrato verificado por tsc)
            └── *.json          um arquivo por captura (envelope, seção 7.3)
```

Testes de unidade ficam **ao lado** do módulo (`distance.ts` +
`distance.test.ts`); testes de fluxo em `src/test/flows/`.

#### 2.1 tsconfig.app.json

`strict: true`, `noUncheckedIndexedAccess: true` (acessar `arr[0]` vira
`T | undefined` — pega exatamente o bug de "lista vazia" que importa aqui),
`noImplicitOverride`, `noFallthroughCasesInSwitch`, `verbatimModuleSyntax`,
`resolveJsonModule: true` (fixtures), `noEmit: true`. Não ligar
`exactOptionalPropertyTypes` (custo alto de atrito com bibliotecas, ganho
pequeno aqui).

### 3. Rotas

Roteamento **declarativo** do React Router (`<Routes>/<Route>`), não o "data
mode" com loaders. Motivo: quem busca dados é o TanStack Query; loaders do
router duplicariam esse papel. Testar fica simples com `MemoryRouter`.
Atenção: a partir da v7 o pacote é **`react-router`** (o
`react-router-dom` virou só reexport) — confirmar no passo 0.

| Rota | Página | Estado na URL |
|---|---|---|
| `/` | `HomePage` | `?q=<termo>` (busca) e `?import=<jobId>` (import em andamento) — recarregar a página retoma o estado; usar `replace` ao digitar para não poluir o histórico |
| `/objects/:slug` | `ObjectPage` | — |
| `/categories/:code` | `CategoryPage` | `?page=<n>` (1-based) |
| `*` | `NotFoundPage` | — |

Título da aba: elemento `<title>` dentro da página (React 19 eleva para o
`<head>`) — confirmar React ≥ 19 no passo 0; se não, usar `document.title`
num efeito.

### 4. Componentes — responsabilidade e props

(Props em notação TS; `?` = opcional. Nenhum componente faz `fetch` direto —
usam os hooks de `api/queries.ts`.)

#### 4.1 Layout e UI genérica

| Componente | Props | Responsabilidade |
|---|---|---|
| `AppShell` | `{ children: ReactNode }` | Header (logo → `/`; `SearchBox` compacta fora da home, que navega para `/?q=`), `<main id="conteudo">`, footer. |
| `SkipLink` | — | "Pular para o conteúdo" (primeiro foco da página). |
| `VisuallyHidden` | `{ children }` | Texto só para leitor de tela. |
| `LoadingState` | `{ label: string }` | `role="status"`, `aria-live="polite"`, texto visível + animação que respeita `prefers-reduced-motion`. |
| `ErrorState` | `{ error: unknown; onRetry?: () => void }` | Mostra `ApiRequestError.message` (texto da API, pt-BR) ou mensagem genérica de rede. `role="alert"`. |
| `ExternalLink` | `{ href: string; children: ReactNode }` | Passa `href` por `safeUrl`; se não for `https:`, renderiza só o texto (sem link). `target="_blank" rel="noopener noreferrer"` + "(abre em nova aba)" via `VisuallyHidden`. **Único** lugar que cria `<a>` externo. |

#### 4.2 Home

| Componente | Props | Responsabilidade |
|---|---|---|
| `HomePage` | — | Compõe `Hero` + `SearchBox` (grande, `autoFocus`) + `CategoryShortcuts` + área de resultados/import. Lê/escreve `q` e `import` em `useSearchParams`. |
| `Hero` | — | "GALAXY EXPLORER" (h1) + "Explore o universo através de dados reais." |
| `CategoryShortcuts` | — | `useCategories()` + `CATEGORY_SHORTCUTS`. Enquanto carrega: chips em esqueleto com `aria-busy`. Se o endpoint falhar: chips **todos desabilitados** com "indisponível" (nunca ativar no escuro). |
| `CategoryChip` | `{ label: string; to: string \| null; count: number \| null; disabledReason: string \| null }` | Ativo → `<Link>` com contagem ("Galáxias · 39"). Desabilitado → elemento não interativo com `aria-disabled="true"` e o texto "em breve" visível (não só cor). |

#### 4.3 Busca e importação

| Componente | Props | Responsabilidade |
|---|---|---|
| `SearchBox` | `{ value: string; onChange: (v: string) => void; onSubmit?: () => void; variant: 'hero' \| 'compact'; autoFocus?: boolean }` | Input controlado, `type="search"`, `<label>` (visualmente oculta), `maxLength={100}` (limite do `SearchController`), placeholder "Pesquisar um objeto…". |
| `useDebouncedValue` | `(value: T, delayMs: number) => T` | Hook puro de debounce. |
| `SearchResults` | `{ query: string }` | Usa `useSearch(debounced)`. Estados: ocioso (query vazia) / carregando / erro (400 mostra `details[0].message`) / com hits / vazio. Anuncia "N resultados" em `role="status"`. Mostra `ImportOffer` quando **não há hit EXACT** (lista vazia **ou** só FUZZY — ver risco R7). |
| `SearchResultItem` | `{ hit: SearchHit }` | `<Link to=/objects/:slug>`: `displayName`, `categoryName ?? "Sem categoria"`, "corresponde a: `matchedIdentifier`", selo "aproximado" quando `matchType === 'FUZZY'`. |
| `ImportOffer` | `{ query: string; onStarted: (jobId: number) => void }` | Botão explícito "Buscar "X" no SIMBAD e importar". **Nunca dispara import automaticamente** (cada import consulta serviço externo; disparar a cada tecla seria abuso do CDS). Usa `useCreateImport()`. 429 → mensagem com `retryAfterSeconds` e botão desabilitado com contagem regressiva. 400 → `details[0].message`. |
| `ImportProgress` | `{ jobId: number }` | `useImportJob(jobId)` com polling. Mensagens de `importStatus.ts`. `succeeded` com 1 slug → navega para `/objects/:slug`; com >1 → lista de links; com `message` ("já existia no catálogo") → mostra a mensagem antes de navegar. `not_found`/`failed` → `job.message` da API (já é só a categoria do erro, sem detalhe interno). 404 do job (retenção/reinício) → "Importação não encontrada (pode ter expirado)". Teto de polling (`TIMINGS.importPollMaxMs`) → "Ainda processando — tente novamente mais tarde", para o polling. Região `role="status" aria-live="polite"`. |

#### 4.4 Página do objeto

| Componente | Props | Responsabilidade |
|---|---|---|
| `ObjectPage` | — | `useParams().slug` → `useObject(slug)`. 404 → "Este objeto não está no catálogo local" + link para `/?q=<slug>`. Ordem: `ObjectHeader`, `ImagePlaceholder`, grade de cards (`PositionCard` + um `PropertyGroupCard` por grupo), `SourcesSection`. |
| `ObjectHeader` | `{ detail: ObjectDetail }` | h1 = `displayName`; nome da categoria (`category?.namePt ?? "Sem classificação"`); `existenceStatus` só se ≠ `confirmed`; `ClassificationExplainer`; `IdentifierList`. |
| `ClassificationExplainer` | `{ category: Category \| null; sourceTypes: SourceType[] }` | `<details><summary>Por que esta classificação?</summary>`: `category.basis` + por fonte "SIMBAD: tipo `sourceType`, morfologia `morphType`". |
| `IdentifierList` | `{ identifiers: Identifier[] }` | Nomes comuns (`isCommonName`) em destaque; demais em `<details>` "N identificadores", agrupados por `catalog`. |
| `ImagePlaceholder` | — | Bloco "Imagem não disponível" com texto explicando que imagens com crédito chegam em bloco futuro. Sem foto decorativa que pareça ser do objeto. |
| `PositionCard` | `{ position: Position \| null }` | RA/Dec sexagesimal + graus, `frame`/`epoch`, `ProvenanceLine` (fonte + referência + data). `null` → "Posição não disponível na fonte". |
| `PropertyGroupCard` | `{ label: string; properties: Property[]; byId: MeasurementIndex }` | `<section aria-labelledby>` com h2 = rótulo do grupo; lista de `PropertyRow`. |
| `PropertyRow` | `{ property: Property; byId: MeasurementIndex }` | Rótulo (+ qualificador: "Magnitude aparente (V)"). `available=false` → `AbsenceNotice`. Disponível → `MeasurementValue` do preferido + `AlternativeValues`. Disponível **sem** preferido (só alternativas) → "Sem valor preferido definido" + alternativas abertas — nunca promover um valor silenciosamente. `id="prop-<code>-<qualifier>"`. |
| `MeasurementValue` | `{ measurement: Measurement; propertyCode: string; byId: MeasurementIndex }` | Valor formatado (`domain/format/measurement`), distância com pc **e** anos-luz, `EpistemicBadge`, `ProvenanceLine`. Se `computed_by_us`: "calculado a partir de: <rótulo da propriedade de origem>" (via `derivedFrom` + `byId`, link âncora para a linha de origem) + `method`. `<details>` "detalhes": `originalValue originalUnit ± originalError`, `dataset`. `id="m-<id>"`. |
| `EpistemicBadge` | `{ epistemic: string }` | Rótulo + ícone + cor (nunca só cor), `title`/descrição curta. Valor desconhecido → mostra o código cru com "natureza não catalogada" (não quebra). |
| `ProvenanceLine` | `{ source: string; reference: Reference \| null; retrievedAt: string }` | "Fonte: SIMBAD · Ref.: `ExternalLink(adsUrl, bibcode)` · coletado em dd/mm/aaaa". Sem referência → "sem referência bibliográfica na fonte". Usa `reference.adsUrl` **como veio da API** (já com o bibcode encoded; não reconstruir a URL no front — uma fonte de verdade). |
| `AbsenceNotice` | `{ absence: Absence }` | Texto principal "Não disponível na fonte" quando `code === NOT_PROVIDED_BY_SOURCE`; senão `absence.message` (texto da API). Se `snr != null`: "S/N da paralaxe = 3,2". Estilo neutro, não de erro. |
| `AlternativeValues` | `{ preferred: Measurement \| null; alternatives: Measurement[]; propertyCode: string; byId: MeasurementIndex }` | Nada se `alternatives` vazio. Senão `<details>` "Outros valores (N)" com um `MeasurementValue` por alternativa; `ConflictBadge` no summary quando `detectConflict` diz que divergem. |
| `ConflictBadge` | `{ result: ConflictResult }` | "Fontes divergem" + explicação da regra (3σ combinados ou >10%). |
| `SourcesSection` | `{ sources: Source[] }` | h2 "Fontes"; por fonte: nome, `ExternalLink` para `homepageUrl` e `termsUrl`, e `acknowledgment` **literal** em `<blockquote>` com `white-space: pre-line` (o texto do SIMBAD tem `\n`). |

`MeasurementIndex` = `ReadonlyMap<number, { property: Property; measurement: Measurement }>`,
construído uma vez por página (função pura em `domain/displayGroups.ts`).

#### 4.5 Página de categoria

| Componente | Props | Responsabilidade |
|---|---|---|
| `CategoryPage` | — | `useParams().code` + `?page`. h1 = `category.namePt` + "N objetos". 404 → "Categoria inexistente". |
| `ObjectList` | `{ items: CategoryObjectItem[] }` | Grade de links (nome + subcategoria). |
| `Pagination` | `{ page: number; pageCount: number; onPage: (n: number) => void }` | `<nav aria-label="Paginação">`, anterior/próxima + "página X de Y". |

#### 4.6 safeUrl / ExternalLink

`safeUrl(raw: string): string | null` — `new URL(raw)`; aceita só protocolo
`https:`; qualquer outra coisa (incluindo `javascript:`, `http:`, relativo,
inválido) → `null`. URLs vêm do banco (fontes) e da API (ADS), hoje
confiáveis, mas o custo é zero e fecha a classe de bug.

### 5. Visual

#### 5.1 Abordagem de estilo: CSS Modules + tokens em CSS custom properties

**Por que CSS Modules:**
- Vem embutido no Vite — **zero dependência nova** e zero runtime.
- Classes com escopo por arquivo: `Card.module.css` não vaza para outro
  componente, sem convenção de nomes tipo BEM.
- É CSS de verdade: o que você aprender aqui vale em qualquer projeto.

**Alternativas descartadas:**
- *Tailwind*: rápido de escrever, mas é uma segunda linguagem (classes
  utilitárias) e dependência/configuração extra; os tokens viveriam num
  arquivo de config JS em vez de CSS.
- *CSS-in-JS (styled-components/Emotion)*: custo de runtime e
  incompatibilidades conhecidas com React 19/streaming; ganho pequeno num
  app sem tema dinâmico.

**Tokens** em `styles/tokens.css` (`:root { --... }`) consumidos pelos
módulos via `var(--...)`. Tipos dos módulos já vêm de `vite/client`.

#### 5.2 Tokens (nomes e intenção — valores finais a calibrar com verificador de contraste)

| Grupo | Tokens | Intenção |
|---|---|---|
| Cor de fundo | `--color-bg`, `--color-bg-glow`, `--color-surface`, `--color-surface-raised`, `--color-border` | Quase-preto azulado (ex.: `#05070d`), superfícies em vidro escuro com borda sutil. |
| Texto | `--color-text`, `--color-text-muted`, `--color-text-faint` | `--color-text` ≥ 12:1 sobre o fundo; `--color-text-muted` **≥ 4,5:1** (WCAG AA); `--color-text-faint` só para decoração, nunca para informação. |
| Acento | `--color-accent`, `--color-accent-strong`, `--color-link` | Ciano/azul-gelo; link precisa de ≥ 4,5:1 e sublinhado (não só cor). |
| Semânticas | `--color-warning` (conflito), `--color-absent` (ausência, neutro), `--color-danger` (erro de rede/import) | Ausência **não** é erro: tom neutro. |
| Epistêmico | `--epi-observed`, `--epi-derived`, `--epi-computed`, `--epi-estimated`, `--epi-model` | Cinco cores distinguíveis também por quem tem daltonismo comum; o selo sempre tem texto + ícone. |
| Tipografia | `--font-display`, `--font-body`, `--font-mono`; escala `--text-xs … --text-3xl`, `--text-hero` com `clamp()` | Display geométrica forte (sugestão: Space Grotesk) para títulos; corpo legível (sugestão: Inter) com `font-variant-numeric: tabular-nums` nos valores. |
| Espaço | `--space-1 … --space-10` (base 4px) | |
| Forma | `--radius-sm/md/lg`, `--shadow-card`, `--glow-accent` | |
| Foco | `--focus-ring` | Anel de 2–3px em `:focus-visible`, contraste ≥ 3:1 com o entorno. |
| Movimento | `--duration-fast/slow`, `--ease-out` | Tudo desligado em `prefers-reduced-motion: reduce`. |
| Layout | `--content-max` (~72rem), `--card-min` (~22rem) | Grade `repeat(auto-fill, minmax(min(100%, var(--card-min)), 1fr))` → 1 coluna no celular sem media query. |

Breakpoints (custom property não funciona em `@media`, então ficam
documentados em comentário no `tokens.css`): 40rem e 64rem. Mobile-first.

**Fontes:** auto-hospedadas via pacotes `@fontsource-variable/*` (importadas
no `main.tsx`). Motivo: sem chamada a Google Fonts (privacidade, funciona
offline). São **dependências novas** → passam pelo `yagyu`.

**Fundo:** gradiente radial sutil ("brilho de nebulosa") puramente
decorativo, `aria-hidden`, sem pontos que imitem um céu real — num app cujo
valor é "dados reais", decoração não pode parecer dado.

#### 5.3 Acessibilidade básica

- `SkipLink`, landmarks (`header`, `main`, `nav`, `footer`), um `h1` por página.
- `:focus-visible` com `--focus-ring` em todo interativo; nunca `outline: none` sem substituto.
- Carregamento: `aria-busy="true"` na região + `role="status"` com texto.
- Resultados de busca e status de import anunciados em `aria-live="polite"`.
- Disclosures com `<details>/<summary>` nativos (teclado e leitor de tela de graça; **funciona no celular**, onde tooltip de hover não existe — por isso "por que esta classificação?" é disclosure, não tooltip).
- Atalhos desabilitados: texto "em breve" visível + `aria-disabled`.
- Lint `jsx-a11y` (seção 8).

### 6. Dev: proxy do Vite

- `vite.config.ts` lê `GALAXY_API_TARGET` com `loadEnv(mode, cwd, '')`;
  default `http://127.0.0.1:8081`.
  - Sem prefixo `VITE_` **de propósito**: só variáveis `VITE_*` vão para o
    bundle do navegador; o alvo do proxy é configuração de dev, não deve
    vazar.
  - `127.0.0.1` e não `localhost`: o backend escuta só em IPv4 loopback
    (`server.address`), e o Node pode resolver `localhost` para `::1`
    primeiro → "connection refused" intermitente.
- `server.proxy['/api'] = { target, changeOrigin: true }`. `changeOrigin`
  faz o `Host` chegar como `127.0.0.1:8081`, compatível com a allowlist de
  `Host` que o `yagyu` sugeriu como defesa futura contra DNS rebinding.
- `server.host` fica no default (loopback). **Não** usar `--host` nem
  `allowedHosts: true` (a checagem de Host do dev server do Vite é a defesa
  dele contra DNS rebinding — e o proxy dá acesso ao `POST /imports`).
- Sem CORS no backend (recomendação do `yagyu`): o navegador só fala com a
  origem do Vite.
- O app chama sempre caminho relativo `/api/v1/...` (`API_BASE` em
  `config.ts`).
- Backend em 8081: `SERVER_PORT=8081` (ou `--server.port=8081`); documentar
  no README do frontend. Não mudar o default do `application.yml` neste bloco.

---

## Interfaces/contratos

### A. Endpoints novos do backend

`GET /api/v1/categories`

- 200: `{ "categories": [ { "code", "namePt", "nameEn", "objectCount" } ] }`
- Só categorias **raiz** (`parent_id IS NULL`), todas (inclusive com 0).
  `objectCount` conta objetos cuja `category_id` está na subárvore da raiz.
  Ordem: `name_pt`.
- Assinaturas Java:
  - `CategoryRepository.findRootsWithCounts(): List<CategoryCount>`
  - `record CategoryCount(int id, String code, String namePt, String nameEn, long objectCount)`
  - `CategoryQueryService.roots(): List<CategoryCount>`
  - `record CategoryListResponse(List<Item> categories)` com `record Item(String code, String namePt, String nameEn, long objectCount)`

`GET /api/v1/categories/{code}/objects?limit=24&offset=0`

- `code` fora de `^[a-z_]{1,40}$` → 404 `CATEGORY_NOT_FOUND` sem consultar o
  banco (mesmo padrão do slug em `ObjectController`). Inexistente → 404.
- `limit` 1..100 (default 24), `offset` 0..10000 (default 0); fora disso →
  400 `VALIDATION_FAILED` via `@Min/@Max` (o `ApiExceptionHandler` já trata
  `HandlerMethodValidationException`).
- Aceita raiz **ou** subcategoria (sempre a subárvore).
- 200: `{ "category": {code, namePt, nameEn}, "total", "limit", "offset", "items": [ {slug, displayName, categoryCode, categoryName} ] }`
- Ordem: `display_name COLLATE natural_sort, slug`.
- Assinaturas Java:
  - `CategoryRepository.findSubtreeIds(int categoryId): List<Integer>` (inclui o próprio id)
  - `CelestialObjectRepository.findByCategoryIds(Collection<Integer> ids, int limit, int offset): List<ObjectRow>`
  - `CelestialObjectRepository.countByCategoryIds(Collection<Integer> ids): long`
  - `CategoryQueryService.objectsOf(String code, int limit, int offset): Optional<CategoryPage>`
  - `record CategoryPage(CategoryRow category, long total, int limit, int offset, List<Item> items)` com `record Item(ObjectRow object, CategoryRow category)` — a categoria do item vem de um mapa id→linha montado uma vez (como o `categoryCache` do `SearchService`), não uma consulta por item.
  - `record CategoryObjectsResponse(Category category, long total, int limit, int offset, List<Item> items)`

### B. Tipos TypeScript (`src/api/types.ts`) espelhando os DTOs reais

Regras de tradução Java → TS:

- Campo documentado "pode ser `null`" ou tipo wrapper (`Double`, `Long`) → `T | null` (o Jackson do Spring serializa `null` explicitamente; confirmar nas fixtures).
- `Instant` → `string` (ISO-8601). **Confirmar pela fixture** (Jackson 3 escreve datas como texto por padrão); se vier número, corrigir o tipo, não a fixture.
- `long`/`double` → `number` (ids são `bigserial` pequenos; sem risco de 2⁵³ na prática).
- **Vocabulário aberto** para enums do backend: `type Epistemic = 'observed' | ... | (string & {})`. Motivo: (1) o backend pode ganhar valor novo sem o front quebrar — a UI tem fallback; (2) permite que a fixture JSON (cujo tipo inferido é `string`) seja atribuída ao tipo declarado, e é isso que faz o `tsc` verificar o contrato (seção 7.3).

| Tipo TS | Origem Java (arquivo) | Campos |
|---|---|---|
| `SearchResponse` | `api/search/SearchResponse.java` | `query: string; normalizedQuery: string; hits: SearchHit[]` |
| `SearchHit` | `api/search/SearchHitDto.java` | `slug: string; displayName: string; categoryCode: string \| null; categoryName: string \| null; matchedIdentifier: string; matchType: MatchType; score: number` |
| `MatchType` | `catalog/read/MatchType.java` (serializado com `.name()` no `SearchController`) | `'EXACT' \| 'FUZZY' \| (string & {})` |
| `CreateImportRequest` | `api/imports/CreateImportRequest.java` | `query: string` (1..100, charset validado **só no backend**; o front mostra `details[0].message` do 400 em vez de duplicar a regex) |
| `ImportJob` | `api/imports/ImportJobResponse.java` | `id: number; query: string; status: ImportJobStatus; message: string \| null; resultSlugs: string[]; runId: number \| null; createdAt: string; startedAt: string \| null; finishedAt: string \| null` |
| `ImportJobStatus` | `ingestion/jobs/ImportJobStatus.java` (`dbValue()` minúsculo) | `'queued' \| 'running' \| 'succeeded' \| 'not_found' \| 'failed' \| (string & {})` |
| `ObjectDetail` | `api/object/ObjectDetailResponse.java` | `slug: string; displayName: string; category: Category \| null; existenceStatus: string; sourceTypes: SourceType[]; identifiers: Identifier[]; position: Position \| null; properties: Property[]; sources: Source[]` |
| `Category` | `ObjectDetailResponse.Category` | `code: string; namePt: string; nameEn: string; basis: string \| null` |
| `SourceType` | `ObjectDetailResponse.SourceType` | `source: string; sourceRef: string \| null; sourceType: string; morphType: string \| null` |
| `Identifier` | `ObjectDetailResponse.Identifier` | `identifier: string; catalog: string \| null; isCommonName: boolean; source: string \| null` (fonte `null` = alias curado) — **confirmar na fixture se o Jackson serializa `isCommonName` ou `commonName`** (record com accessor `isCommonName()` é caso clássico de divergência) |
| `Position` | `ObjectDetailResponse.Position` | `raDeg: number; decDeg: number; frame: string; epoch: string; source: string; reference: Reference \| null; retrievedAt: string` |
| `Reference` | `ObjectDetailResponse.Reference` + `ObjectDetailMapper.reference` | `bibcode: string; adsUrl: string` |
| `Property` | `ObjectDetailResponse.Property` (montado em `catalog/read/ObjectQueryService.buildProperties`) | `code: string; label: string; group: string; qualifier: string; available: boolean; absence: Absence \| null; preferred: Measurement \| null; alternatives: Measurement[]` |
| `Absence` | `ObjectDetailResponse.Absence` | `code: AbsenceCode; message: string; snr: number \| null` |
| `AbsenceCode` | `catalog/read/AbsenceReason.java` | `'NOT_PROVIDED_BY_SOURCE' \| 'PARALLAX_SNR_BELOW_5' \| 'PARALLAX_WITHOUT_UNCERTAINTY' \| 'PARALLAX_UNCERTAINTY_INVALID' \| 'PARALLAX_NON_POSITIVE' \| (string & {})` |
| `Measurement` | `ObjectDetailResponse.Measurement` (de `catalog/read/MeasurementView.java`) | `id: number; value: number \| null; errMinus: number \| null; errPlus: number \| null; limitFlag: '<' \| '>' \| '~' \| null; valueText: string \| null; unit: string \| null; originalValue: string; originalUnit: string \| null; originalError: string \| null; epistemic: Epistemic; method: string \| null; derivedFrom: number[] \| null; source: string; dataset: string; sourceUrl: string; reference: Reference \| null; retrievedAt: string` |
| `Epistemic` | `catalog/Epistemic.java` (`dbValue()`) | `'observed' \| 'derived_by_source' \| 'computed_by_us' \| 'estimated' \| 'model' \| (string & {})` — note o **quinto** valor, `model`, que a spec do usuário não listou mas o banco aceita |
| `Source` | `ObjectDetailResponse.Source` | `code: string; name: string; homepageUrl: string; termsUrl: string \| null; acknowledgment: string \| null` |
| `ApiErrorBody` | `api/error/ApiError.java` | `{ error: { code: ApiErrorCode; message: string; details: ApiErrorDetail[] \| null } }`, `ApiErrorDetail = { field: string; message: string }` |
| `ApiErrorCode` | `api/error/ApiErrorCode.java` (+ `CATEGORY_NOT_FOUND`) | `'VALIDATION_FAILED' \| 'OBJECT_NOT_FOUND' \| 'IMPORT_NOT_FOUND' \| 'IMPORT_QUEUE_FULL' \| 'CATEGORY_NOT_FOUND' \| 'INTERNAL_ERROR' \| (string & {})` |
| `CategoryListResponse` / `CategorySummary` | `api/category/CategoryListResponse.java` (novo) | `categories: { code; namePt; nameEn; objectCount: number }[]` |
| `CategoryObjectsResponse` / `CategoryObjectItem` | `api/category/CategoryObjectsResponse.java` (novo) | `category: { code; namePt; nameEn }; total; limit; offset; items: { slug; displayName; categoryCode: string \| null; categoryName: string \| null }[]` |

Nota: `unit` em `Measurement` é a **unidade canônica** da propriedade
(`MeasurementView.canonicalUnit`, de `property_definition.canonical_unit` em
V3): `pc`, `mas`, `km/s`, `mag`, `arcmin` ou `null`.

### C. Camada `api/`

- `http.ts`
  - `class ApiRequestError extends Error { status: number; code: string | null; details: ApiErrorDetail[] | null; retryAfterSeconds: number | null }`
  - `requestJson<T>(path: string, init?: RequestInit & { signal?: AbortSignal }): Promise<T>` — prefixa `API_BASE`; resposta não-2xx: se `content-type` for JSON e tiver `error`, preenche `code/message/details`; senão mensagem genérica com o status (o 404/405 padrão do Spring não usa o formato `ApiError`). Lê `Retry-After` (segundos) em 429.
- `endpoints.ts` (todo segmento de path via `encodeURIComponent`)
  - `searchObjects(q: string, signal?: AbortSignal): Promise<SearchResponse>`
  - `getObject(slug: string, signal?): Promise<ObjectDetail>`
  - `createImport(query: string): Promise<ImportJob>` (POST JSON)
  - `getImport(id: number, signal?): Promise<ImportJob>`
  - `listCategories(signal?): Promise<CategoryListResponse>`
  - `listCategoryObjects(code: string, page: { limit: number; offset: number }, signal?): Promise<CategoryObjectsResponse>`
- `queryKeys.ts`: `qk.search(q)`, `qk.object(slug)`, `qk.import(id)`, `qk.categories()`, `qk.categoryObjects(code, page)`.
- `queries.ts`
  - `useSearch(q: string)` — `enabled: q.trim().length > 0`, `placeholderData: keepPreviousData`, `staleTime` ~30s.
  - `useObject(slug: string)`
  - `useCategories()` — `staleTime` longo (5 min).
  - `useCategoryObjects(code: string, page: number)`
  - `useCreateImport()` — `useMutation`.
  - `useImportJob(id: number | null)` — `refetchInterval: (query) => isTerminal(status) ? false : TIMINGS.importPollMs`; ao chegar em `succeeded`, invalida `qk.search(...)` e `qk.categories()` (contagens mudaram).
  - `QueryClient` default: `retry` só para erro de rede/5xx, **nunca** para 4xx (repetir 404/429 é inútil e, no 429, piora a fila).
- `config.ts`: `API_BASE = '/api/v1'`; `TIMINGS = { searchDebounceMs: 300, importPollMs: 1000, importPollMaxMs: 180_000, searchStaleMs: 30_000 }`.

### D. Camada `domain/` (funções puras)

| Módulo | Assinatura | Regra |
|---|---|---|
| `format/number.ts` | `formatNumber(x: number, opts: { significantDigits?: number; fractionDigits?: number }): string` | `Intl.NumberFormat('pt-BR')` (vírgula decimal). Notação científica acima de 1e6 / abaixo de 1e-4 com `×10ⁿ` legível. |
| | `significantDigitsOf(original: string): number \| null` | Conta algarismos significativos do texto original da fonte. |
| `format/uncertainty.ts` | `formatUncertainty(errMinus: number \| null, errPlus: number \| null, digits: …): string \| null` | Iguais → `± x`; diferentes → `+a / −b`; ambos `null` → `null`. |
| | `decimalsForUncertainty(errMinus, errPlus): number` | 2 algarismos significativos da menor incerteza; valor e incerteza com o **mesmo** número de casas. |
| `format/units.ts` | `unitSymbol(code: string \| null): string` | `pc→pc`, `mas→mas`, `km/s→km/s`, `mag→mag`, `arcmin→′`; desconhecido → o próprio código; `null` → `''`. |
| `format/measurement.ts` | `formatMeasurement(m: Measurement): { value: string; uncertainty: string \| null; unit: string; limit: string \| null }` | Numérico: com incerteza → casas pela incerteza; sem incerteza → algarismos de `originalValue` **só se** `originalUnit` = `unit` (mesma escala), senão 4 significativos. Texto (`valueText`) → como veio. `limitFlag` `<`/`>`/`~` → prefixo "<", ">", "≈". |
| `format/distance.ts` | `LY_PER_PC` | **1 pc = 3,261 563 777 anos-luz.** Derivação citada no comentário: pc = 648 000/π au (IAU 2015, Resolução B2); au = 149 597 870 700 m (IAU 2012, Resolução B2); ano-luz = c × ano juliano = 299 792 458 m/s × 365,25 × 86 400 s = 9 460 730 472 580 800 m (IAU). Já é a família de constantes IAU que a Fase 0 §6 manda citar. |
| | `formatDistance(m: Measurement): { pc: string; ly: string; uncertaintyPc: string \| null; uncertaintyLy: string \| null }` | Escala automática: pc / kpc / Mpc; anos-luz / mil / milhões / bilhões de anos-luz. Incerteza convertida pelo mesmo fator (conversão linear). |
| `format/coordinates.ts` | `formatRa(deg: number): string`, `formatDec(deg: number): string` | `05h 55m 10,3s` / `+07° 24′ 25″`. |
| `format/date.ts` | `formatRetrievedAt(iso: string): string` | `dd/mm/aaaa`, fuso UTC (data da coleta, não a hora local). |
| `epistemic.ts` | `epistemicInfo(code: string): { label: string; description: string; variant: 'observed' \| 'derived' \| 'computed' \| 'estimated' \| 'model' \| 'unknown' }` | observed → "Observado"; derived_by_source → "Derivado pela fonte"; computed_by_us → "Calculado por nós"; estimated → "Estimado"; model → "Modelo"; outro → código cru + "natureza não catalogada". |
| `displayGroups.ts` | `groupProperties(props: Property[]): { group: string; label: string; properties: Property[] }[]` | Ordem fixa: astrometry "Astrometria", kinematics "Cinemática", photometry "Fotometria", classification "Classificação", morphology "Morfologia"; grupo desconhecido vai ao fim com o código cru como rótulo (nunca some). |
| | `indexMeasurements(props: Property[]): MeasurementIndex` | id → (propriedade, medição), preferidas e alternativas. |
| `conflict.ts` | `detectConflict(values: Measurement[]): { diverges: boolean; rule: '3sigma' \| '10pct' \| 'insufficient' }` | Fase 0 §7: diverge se |a−b| > 3·√(σa²+σb²) (σ = média de errMinus/errPlus); sem incerteza em algum → diverge se diferença relativa > 10%; < 2 valores numéricos → `insufficient`. |
| `categoryShortcuts.ts` | `CATEGORY_SHORTCUTS: readonly { label: string; categoryCode: string \| null; pendingNote: string }[]` | Ver tabela abaixo. |
| | `resolveShortcuts(shortcuts, categories: CategorySummary[] \| null): { label; to: string \| null; count: number \| null; disabledReason: string \| null }[]` | Ativo **sse** `categoryCode` existe na resposta **e** `objectCount > 0`. Código ausente da resposta → desabilitado (protege contra renomear categoria no backend). Resposta `null` (erro) → todos desabilitados "indisponível". |
| `importStatus.ts` | `importStatusView(job: ImportJob): { tone: 'progress' \| 'success' \| 'notFound' \| 'error'; text: string; terminal: boolean }` | ver seção E. |
| `safeUrl.ts` | `safeUrl(raw: string \| null): string \| null` | só `https:`. |

**[DECISÃO NOVA] Atalhos da home** (`CATEGORY_SHORTCUTS`, ordem = a da spec):

| Rótulo | `categoryCode` | Situação esperada hoje |
|---|---|---|
| Estrelas | `star` | ativo (Betelgeuse + estrelas do seed) |
| Planetas | `null` | desabilitado — Sistema Solar é fase 2 (JPL); não existe categoria |
| Galáxias | `galaxy` | ativo |
| Nebulosas | `nebula` | ativo |
| **Aglomerados** | `cluster` | ativo — **acréscimo à spec**: há dados reais (M13, M45, M54…) e o usuário os listou como disponíveis; vetável |
| Cometas | `null` | desabilitado — fase 2 (JPL SBDB) |
| Asteroides | `null` | desabilitado — fase 2 |
| Exoplanetas | `exoplanet` | desabilitado enquanto `objectCount = 0` |
| Buracos Negros | `black_hole` | desabilitado: Sgr A* é classificado `x_ray_source` (descoberta 2 da Fase 0 — "buraco negro" é interpretação, não tipo de catálogo). Ele continua achável pela busca. |

Mapeamento 1:1 código → atalho. AGNs sem morfologia caem na raiz `agn`, que
**não** tem atalho na spec: ficam fora de "Galáxias" (achar via busca).
Fundir `agn` em "Galáxias" seria uma reinterpretação de categoria no
frontend — se o usuário quiser, a mudança certa é na hierarquia do banco,
não aqui.

### E. Fluxo busca → importação → polling

```
digita "Vega" ──debounce 300ms──► GET /search?q=Vega
   hits com EXACT ──► lista; fim
   hits vazios ou só FUZZY ──► lista (se houver) + ImportOffer
        clique ──► POST /imports {"query":"Vega"}
            400 ──► details[0].message
            429 ──► "Fila de importação cheia. Tente de novo em N s." (N = Retry-After; botão volta após N s)
            202 ──► URL ganha ?import=<id> ──► ImportProgress
                GET /imports/<id> a cada 1s:
                  queued     ──► "Na fila de importação…"
                  running    ──► "Resolvendo o nome no Sesame (CDS) e importando de SIMBAD…"
                  succeeded  ──► (message? mostra) ──► 1 slug: navega /objects/<slug>; >1: lista
                  not_found  ──► job.message ("Objeto não encontrado em SIMBAD")
                  failed     ──► job.message (categoria do erro, ex. "Fonte indisponível no momento…")
                  404        ──► "Importação não encontrada (pode ter expirado)"
                  > 3 min    ──► para o polling: "Ainda processando — tente novamente mais tarde"
```

**[DECISÃO NOVA] Mensagens de fase.** A spec pede "Resolvendo no Sesame…"
e depois "Importando de SIMBAD…" como etapas separadas. O backend **não
expõe a fase**: `ImportJobResponse.status` só tem
`queued|running|succeeded|not_found|failed`, e a resolução no Sesame e o
TAP do SIMBAD acontecem os dois dentro de `running`
(`ImportJobWorker.execute` → `IngestionPipeline.run`). Trocar o texto por
tempo decorrido seria **inventar progresso**. Decisão: durante `running`,
uma única mensagem verdadeira que nomeia as duas etapas. Se o usuário quiser
as fases separadas, é um bloco de backend próprio (coluna `phase` em
`import_job` + callback do pipeline para o worker), fora deste bloco.

---

## 7. Testes

### 7.1 Backend — `CategoryApiIntegrationTest`

Estende `IntegrationTestBase`, ingere `Samples.QUERIES` no `@BeforeEach`
(mesmo padrão de `SearchApiIntegrationTest`). Asserções **robustas ao
mapeamento** (não fixar contagens de casos ambíguos):

1. `GET /categories`: só raízes; inclui `star`, `galaxy`, `nebula`, `cluster`, `black_hole`, `exoplanet` (com 0 onde não houver).
2. Invariante: soma de `objectCount` das raízes = nº de objetos com categoria (cada objeto conta uma vez — as subárvores das raízes particionam o conjunto).
3. `star` ≥ 1; `black_hole` = 0; `exoplanet` = 0.
4. `GET /categories/galaxy/objects` contém `messier-31` (cuja categoria é a filha `galaxy_spiral` — prova que a subárvore funciona) e `items[].categoryCode = galaxy_spiral` para ele.
5. Subcategoria direta: `GET /categories/galaxy_spiral/objects` funciona.
6. Paginação: `limit=1` → 1 item, `total` igual ao sem limite; `offset` além do total → lista vazia, 200.
7. Ordenação natural: com os Messier do sample, `M 13` vem antes de `M 54` e antes de `M 1xx` se houver (montar asserção com o que o sample tiver).
8. `code` inexistente e `code` fora do formato (`GALAXY`, `a/b`) → 404 `CATEGORY_NOT_FOUND`.
9. `limit=0`, `limit=101`, `offset=-1` → 400 `VALIDATION_FAILED`.

### 7.2 Frontend — ferramentas

- **Vitest** (mesma config do Vite; sem segunda pipeline de build), ambiente **jsdom**.
- **Testing Library** (`@testing-library/react`, `user-event`, `jest-dom`).
- **MSW 2** (`msw/node`) servindo as fixtures. Escolhido em vez de mockar
  `fetch`: o teste exercita o `http.ts` real — cabeçalho `Retry-After`,
  corpo de erro, status — do jeito que o navegador veria. `onUnhandledRequest:
  'error'`: qualquer requisição sem fixture falha o teste (impede dado
  inventado entrar pela porta dos fundos).
- Tempos: testes de fluxo sobrescrevem `TIMINGS` (`vi.mock('../config')`)
  com debounce/polling de poucos ms. Mockar configuração não é mockar dado.
- `renderApp(path)`: `MemoryRouter initialEntries=[path]` + `QueryClient`
  novo por teste com `retry: false`.

### 7.3 Fixtures reais — `scripts/capture-fixtures.mjs`

Script Node (fetch nativo do Node 24; evita as armadilhas de PowerShell com
BOM/escape). Roda contra o backend local **com o seed Messier completo**
carregado.

**Envelope** de cada arquivo `src/test/fixtures/<nome>.json`:
`{ "request": {method, path, body}, "status", "headers": {content-type, location, retry-after}, "capturedAt", "body": <JSON exatamente como veio> }`.
O `body` não é editado. `manifest.json` lista tudo com o comando/condição de
captura.

**Capturas** (nomes indicativos):

| Fixture | Requisição / condição |
|---|---|
| `search-m31` | `GET /search?q=M31` (EXACT) |
| `search-fuzzy` | `GET /search?q=Betelgeuze` (FUZZY) |
| `search-empty-vega` | `GET /search?q=Vega` **antes** do import |
| `search-400` | `GET /search?q=***` (sem caractere pesquisável) |
| `categories` | `GET /categories` |
| `category-galaxy-p1` | `GET /categories/galaxy/objects?limit=24&offset=0` |
| `category-404` | `GET /categories/nao_existe/objects` |
| `object-*` | **todos** os objetos (iterar as raízes com `/categories/{root}/objects`), e salvar: o 1º exemplo real de cada `absence.code`, de cada `epistemic`, de `limitFlag` não nulo, de propriedade com `alternatives` não vazio, de `position: null`, mais `messier-31`, `betelgeuse` (distância `computed_by_us` a partir da paralaxe), e o objeto de Sgr A* (slug lido da busca, não presumido) |
| `object-404` | `GET /objects/nao-existe` |
| `import-vega-*` | `POST /imports {"query":"Vega"}` → 202 + polling; salvar **cada status distinto** observado (`queued`/`running`/`succeeded`) |
| `object-vega` | `GET /objects/<slug de resultSlugs>` após o sucesso |
| `import-not-found-*` | `POST` + polling de `XyzzyNotAnObject123` (o termo inexistente que o Bloco 1 já usa em `Samples`) |
| `import-400` | `POST /imports {"query":"<b>x</b>"}` (charset inválido → details reais) |
| `import-failed-*` | **2ª execução do backend** com `GALAXY_SIMBAD_SESAME_URL=http://127.0.0.1:9/` (porta sem serviço): o código real cai em `SOURCE_UNAVAILABLE` e devolve a mensagem real. Registrar a condição no manifest. |
| `import-429` | mesma 2ª execução com `GALAXY_IMPORTS_QUEUE_CAPACITY=1`: 3 POSTs seguidos com termos distintos → 1º rodando, 2º na fila, **3º = 429 real com `Retry-After`**. Não toca o CDS (Sesame apontado para porta morta). |
| `import-404` | `GET /imports/999999999` |

(Os nomes de variável de ambiente seguem o relaxed binding do Spring para
`galaxy.simbad.sesame-url` e `galaxy.imports.queue-capacity`; o `sasaki`
confirma que o backend os aceita antes de capturar.)

**`coverage.md`** (gerado pelo script): tabela "estado da UI → fixture que o
exemplifica | **sem exemplo real**". Hoje é provável que `alternatives`
(conflito), `estimated`, `model` e alguns `PARALLAX_*` não tenham exemplo no
seed — ficam listados como gap, não inventados.

**Contrato verificado pelo `tsc`:** `fixtures/index.ts` importa cada JSON e
exporta com anotação de tipo (ex.: `export const objectM31: Fixture<ObjectDetail> = …`).
Se o backend mudar um campo e as fixtures forem recapturadas, o `npm run
typecheck` acusa a divergência entre os tipos TS e a resposta real. É o
mais perto de "teste de contrato" sem dependência nova.

### 7.4 Casos de teste do frontend

Unidade (puras; entradas tiradas das fixtures, saída esperada escrita no teste):

- `uncertainty`/`measurement`: paralaxe de Betelgeuse da fixture → valor e `±` com mesmo nº de casas, vírgula decimal; assimétrico `+a / −b` **se** houver exemplo real (senão gap); `limitFlag` → prefixo; `valueText` (tipo espectral) como veio; unidade `arcmin` → `′`.
- `distance`: `LY_PER_PC` bate com a derivação IAU até 1e-9; distância `computed_by_us` de Betelgeuse em pc e anos-luz; escala Mpc/milhões de anos-luz com uma galáxia que tenha distância na fixture.
- `epistemic`: os 5 códigos + desconhecido.
- `conflict`: **redshift de M31, valores reais da Fase 0 §2 item 4** (SIMBAD −0,0010002 vs NED −0,000991 ± 0,000003) — citar no teste; caso `insufficient`.
- `displayGroups`: ordem; grupo desconhecido não some.
- `categoryShortcuts.resolveShortcuts`: com a fixture `categories` → ativo/inativo conforme contagem; `null` → todos "indisponível". **Teste de sanidade:** todo `categoryCode` não-nulo de `CATEGORY_SHORTCUTS` existe na fixture (pega renome de categoria no backend).
- `importStatus`: cada status capturado.
- `safeUrl`: `https:` ok; `javascript:`, `http:`, relativo, lixo → `null`.
- `http.ts`: fixtures `object-404`, `import-400`, `import-429` → `ApiRequestError` com `code`, `details`, `retryAfterSeconds` corretos.

Componentes:

- `EpistemicBadge`: rótulo textual presente (não só cor).
- `AbsenceNotice`: com propriedade `available:false` real → "Não disponível na fonte"; com `PARALLAX_*` real (se houver) → mensagem da API + S/N.
- `ObjectPage` (`object-m31`): h1, categoria, `<details>` de classificação contém `category.basis`; um card por grupo presente; link ADS com `href === reference.adsUrl` da fixture; seção Fontes contém o `acknowledgment` literal; "Imagem não disponível".

Fluxos (`src/test/flows/`):

- `search-import.test.tsx`: `/` → digita "Vega" → mensagem de vazio + oferta → clique → sequência MSW `import-vega-*` → mensagens de status (`running` com o texto Sesame+SIMBAD) → navegou para `/objects/<slug>` e renderizou `object-vega`.
- não encontrado: sequência `import-not-found-*` → mensagem da API.
- falha: `import-failed-*` → mensagem `SOURCE_UNAVAILABLE` real.
- 429: `import-429` → "tente de novo em 30 s" (valor do header capturado, não hardcoded no teste) e botão desabilitado.
- FUZZY: `search-fuzzy` → lista com selo "aproximado" **e** oferta de importação.
- `routing.test.tsx`: `/objects/messier-31` renderiza; `/objects/nao-existe` → mensagem de 404; `/categories/galaxy` lista; `/categories/nao_existe` → 404; `/rota/qualquer` → `NotFoundPage`; chip ativo navega; chip desabilitado não é link.

---

## 8. Lint e typecheck

- `eslint.config.js` (flat): `@eslint/js` recommended, `typescript-eslint`
  (recommendedTypeChecked), `eslint-plugin-react-hooks`,
  `eslint-plugin-react-refresh`, `eslint-plugin-jsx-a11y`.
- **Regra que força a recomendação do `yagyu`:** `no-restricted-syntax`
  com seletor de atributo JSX `dangerouslySetInnerHTML` → `error`. Sem
  dependência extra; a recomendação deixa de ser só convenção.
- `@typescript-eslint/no-explicit-any: error`.
- Scripts:
  - `"test": "vitest run"` — **nunca** `vitest` puro (modo watch trava o qa-gate).
  - `"lint": "eslint . --max-warnings 0"`
  - `"typecheck": "tsc --noEmit -p tsconfig.app.json && tsc --noEmit -p tsconfig.node.json"` (com project references, `tsc --noEmit` na raiz não checa nada — armadilha do template Vite).

## 9. Docker Compose — decisão: **adiar**

Manter os serviços `backend`/`frontend` comentados como estão. Motivos:

1. O fluxo de desenvolvimento deste bloco é Vite dev server (HMR) + `mvnw`
   local; containers não são usados por ninguém agora.
2. Containerizar o frontend significa build estático + servidor (nginx) +
   repetir o proxy `/api` em outra ferramenta — superfície nova sem uso.
3. Containerizar o backend exige o cuidado de bind que o `application.yml`
   já documenta (`GALAXY_BIND_ADDRESS=0.0.0.0` só dentro do container,
   porta publicada em `127.0.0.1`) e cai no escopo de revisão do `yagyu`.
   Merece um bloco de "empacotamento/deploy" próprio.
4. O proxy já lê `GALAXY_API_TARGET`: quando os serviços existirem, basta
   apontar para `http://backend:8080` — nada no código do app muda.

## 10. qa-gate

`qa-gate.py` roda os comandos com `shell=True` (cmd.exe) e `cwd` = raiz do
repositório. Proposta para `.claude/qa-gate.config.json`:

- `test_cmd`: `cd frontend && npm test && cd ..\backend && .\mvnw.cmd -q -B test`
  (em JSON: `"cd frontend && npm test && cd ..\\backend && .\\mvnw.cmd -q -B test"`)
  — frontend primeiro: leva segundos e falha rápido; o backend (Testcontainers)
  é o lento.
- `lint_cmd`: `cd frontend && npm run lint && npm run typecheck`
  — backend continua sem lint (não existe hoje; não é escopo deste bloco).
- Pré-requisitos: `npm ci` já rodado em `frontend/` e Docker ativo (já era
  exigência do backend). Documentar no README.
- Trocar a config **no passo 2** (quando `frontend/` já tem teste rodando),
  não antes — senão todo commit intermediário do backend é bloqueado.

## 11. Sequência de implementação (passos verificáveis)

| # | Passo | Verificação |
|---|---|---|
| 0 | `npm view <pkg> version` (e `peerDependencies`/`engines` quando relevante) para: `vite`, `@vitejs/plugin-react`, `react`, `react-dom`, `react-router`, `@tanstack/react-query`, `typescript`, `@types/react`, `@types/react-dom`, `@types/node`, `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/dom`, `@testing-library/user-event`, `@testing-library/jest-dom`, `msw`, `eslint`, `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`, `eslint-plugin-jsx-a11y`, `globals`, `@fontsource-variable/inter`, `@fontsource-variable/space-grotesk`. Conferir: `vitest` compatível com o major do `vite`; `@vitejs/plugin-react` idem; `engines` aceitam Node 24; React ≥ 19. Registrar as versões escolhidas no relatório. | lista de versões no relatório do `sasaki` |
| 1 | Backend: V5 + repositórios + `CategoryQueryService` + controller + DTOs + `CATEGORY_NOT_FOUND` + `CategoryApiIntegrationTest`. | `.\mvnw.cmd -q -B test` verde; `curl` manual nos dois endpoints com o seed |
| 2 | Scaffold `npm create vite@latest frontend -- --template react-ts`; ajustar tsconfig strict, ESLint, scripts, proxy, Vitest + setup MSW; um teste-fumaça que faz `fetch` com `AbortSignal` sob MSW+jsdom (ver R10). Atualizar `qa-gate.config.json` e `.gitignore`. | `npm test`, `npm run lint`, `npm run typecheck` verdes; `npm run dev` + backend → `/api/v1/categories` responde pelo proxy |
| 3 | `capture-fixtures.mjs` + captura (incl. a 2ª execução do backend para failed/429) + `manifest.json` + `coverage.md`. | fixtures no disco; `coverage.md` revisado |
| 4 | `api/` (types, http, endpoints, queryKeys, queries) + `fixtures/index.ts` tipado + testes do `http.ts`. | `typecheck` verde contra fixtures reais (contrato confere) |
| 5 | `domain/` completo + testes de unidade. | testes verdes |
| 6 | Tokens, CSS global, fontes, `AppShell`, `SkipLink`, primitivas `ui/`, rotas vazias + `NotFoundPage`. | navegação por teclado no navegador; contraste dos tokens conferido com verificador |
| 7 | Home: `Hero`, `SearchBox`, `CategoryShortcuts`, `SearchResults`. | testes de componente/fluxo de busca |
| 8 | Import: `ImportOffer`, `ImportProgress`. | fluxos busca→import (sucesso, not_found, failed, 429) |
| 9 | Página do objeto (todos os componentes de 4.4). | testes de `ObjectPage`; conferência visual de M31 e Betelgeuse no navegador, celular via DevTools |
| 10 | Página de categoria + testes de roteamento. | `routing.test.tsx` |
| 11 | README do frontend (rodar, porta 8081, recapturar fixtures). | — |
| 12 | `takumi` → `hattori` → `yagyu` (dependências novas, `ExternalLink`, proxy, captura de fixtures) → commit. | review-logs `PASS` |

Commits sugeridos (cada um passa pelo qa-gate): (a) passo 1 — backend;
(b) passos 2–5 — fundação do frontend; (c) passos 6–11 — UI.

---

## Riscos apontados

| # | Risco | Mitigação no plano |
|---|---|---|
| R1 | **Progresso de import falso.** Spec pede fases "Sesame" → "SIMBAD", backend só tem `running`. | Mensagem única verdadeira; fases reais exigem bloco de backend (seção E). |
| R2 | **Política de conflito duplicada.** A regra 3σ/10% (Fase 0 §7) é de domínio e está indo para o frontend; a escolha do preferido está no backend (`PreferencePolicy`). Duas metades da mesma política em linguagens diferentes podem divergir. | `conflict.ts` isolado, puro, com teste citando a §7. **Gatilho para mover ao backend** (campo `diverges` em `Property`): Fase 3 (NED), quando conflito passar a existir de verdade. Hoje, com só SIMBAD, `alternatives` provavelmente vem vazio. |
| R3 | **Estados sem exemplo real.** `alternatives`, `estimated`, `model`, alguns `PARALLAX_*`, incerteza assimétrica podem não existir no seed. | Captura varre todos os objetos; `coverage.md` lista o que falta; teste de componente desse estado **não** é escrito com dado inventado (só a função pura, com valores reais citados). Gap visível para o `takumi`. |
| R4 | **Drift de contrato Java ↔ TS** (tipos espelhados à mão). | Fixtures tipadas + `tsc`; recapturar sempre que um DTO mudar; tipos citam o arquivo Java de origem. Pontos já suspeitos: `isCommonName` vs `commonName` no JSON de record; formato de `Instant`; nulls explícitos. |
| R5 | **Vocabulário de categorias duplicado** (`CATEGORY_SHORTCUTS` repete códigos de V3). | Atalho com código ausente da resposta fica desabilitado; teste de sanidade contra a fixture. |
| R6 | Interpretação de categorias: AGN fora de "Galáxias"; Sgr A* fora de "Buracos Negros"; "Aglomerados" acrescentado. | Explicado na seção D para o usuário decidir; nenhuma reinterpretação no frontend. |
| R7 | **Busca FUZZY bloquear importação**: "Vega" pode casar por trigrama com algo local e a lista não ficar vazia → sem oferta de import. | Oferta aparece sempre que não há hit `EXACT` (coerente com o `ImportJobWorker`, que só considera `EXACT` como "já existe"). |
| R8 | Collation ICU (V5) indisponível em algum Postgres. | Imagem oficial `postgres:17` tem ICU; fallback documentado `ORDER BY display_name, slug`. |
| R9 | `localhost` → `::1` no Node 24 com backend só em IPv4. | Alvo default `127.0.0.1`. |
| R10 | Incompatibilidade conhecida jsdom × fetch nativo do Node: `AbortSignal` do jsdom rejeitado pelo `fetch` do undici ("Expected signal to be an instance of AbortSignal"). TanStack Query passa `signal` para a queryFn. | Teste-fumaça no passo 2 detecta cedo; se ocorrer, o `sasaki` escolhe a correção (alinhar globais no `setup.ts` ou `happy-dom`) e registra no relatório — não remover o `signal` do código de produção. |
| R11 | qa-gate mais lento e com mais pré-requisitos (`npm ci`, Docker). | Frontend primeiro (falha rápido); README documenta. |
| R12 | Dependências novas (React, Router, Query, MSW, fontes, plugins ESLint). | Revisão do `yagyu` (dono de "dependências novas"); lockfile commitado; `npm ci`. |
| R13 | DNS rebinding via dev server/proxy (o proxy abre `POST /imports` para quem alcançar o Vite). | Vite em loopback, sem `--host`, sem `allowedHosts: true`; `changeOrigin` compatível com allowlist de `Host` futura no backend. |
| R14 | Polling eterno se o servidor cair no meio. | Teto `importPollMaxMs`; 404 do job tratado (reinício marca FAILED com "interrompido por reinício do servidor"). |
| R15 | Sobreposição com o existente: `CategoryRepository` passa a ter duas responsabilidades (regras de mapeamento + hierarquia/contagem). | Aceitável (mesma tabela, mesma entidade); toda CTE de hierarquia fica só nele — `CelestialObjectRepository` recebe ids já resolvidos e não conhece a árvore. Se crescer, separar `CategoryTreeRepository`. |
| R16 | Captura de fixtures com efeito colateral: importar "Vega" grava no banco de dev e consulta o CDS. | Esperado (é o que import faz); 1 consulta real; capturas de falha/429 usam Sesame apontado para porta morta — não tocam o CDS. |

---

## ADENDO (orquestrador, 2026-10-06) — PREVALECE sobre o texto acima

**Decisões do usuário:**
1. Progresso da importação: **mensagem única honesta** durante `running` ("Resolvendo o nome no Sesame (CDS) e importando de SIMBAD…"). Sem coluna `phase` no backend.
2. Atalho "Buracos Negros": **desabilitado "em breve"**. Sgr A* continua achável pela busca.
3. Demais [DECISÃO NOVA] do plano: aceitas como propostas (Aglomerados como atalho ativo; Planetas/Cometas/Asteroides desabilitados; Docker Compose do frontend adiado).

**Correção de numeração:** o Bloco 1 já tem migrações **V1–V6** (V5 = stellar types, V6 = measurement.original_error). Qualquer migração nova deste bloco (ex.: collation de ordenação natural) é **V7** em diante. Nunca altere V1–V6 (Flyway checksum).

**Ambiente:** backend local em 127.0.0.1:8081 (8080 ocupada); Postgres do compose em 5433; Docker Desktop pode estar parado — abrir antes de testes Testcontainers. qa-gate roda via cmd.exe (`.\mvnw.cmd`).
