# Galaxy Explorer — frontend

React 19 + TypeScript (strict) + Vite 8, TanStack Query, React Router 8, CSS Modules.
Fala só com a própria origem: `/api/**` é repassado pelo proxy do Vite ao backend Spring.

## Pré-requisitos

- Node 24 (`node --version`).
- Dependências instaladas com o lockfile: `npm ci` (dentro de `frontend/`).
- Para usar o app de verdade: PostgreSQL do compose (porta 5433) e o backend em **127.0.0.1:8081**.

## Rodar

Em três terminais (Git Bash ou PowerShell), a partir da raiz do repositório:

```bash
# 1. banco (Docker Desktop aberto)
docker compose up -d postgres

# 2. backend na porta 8081 (a 8080 desta maquina esta ocupada)
cd backend
SERVER_PORT=8081 ./mvnw spring-boot:run          # Git Bash
# $env:SERVER_PORT=8081; .\mvnw.cmd spring-boot:run   # PowerShell

# 3. frontend
cd frontend
npm run dev
```

Abra **http://127.0.0.1:5173/**.

O alvo do proxy vem de `GALAXY_API_TARGET` (default `http://127.0.0.1:8081`; ver `.env.example`).
O dev server escuta só em loopback — não use `--host` nem `allowedHosts: true`: o proxy dá acesso
ao `POST /api/v1/imports`, e a checagem de Host do Vite é a defesa contra DNS rebinding.

Build de produção: `npm run build` (gera `dist/`); `npm run preview` serve o build em
http://127.0.0.1:4173/ com o mesmo proxy.

## Scripts

| Script | O que faz |
|---|---|
| `npm run dev` | dev server com HMR |
| `npm test` | Vitest uma vez (`vitest run` — nunca o modo watch, que travaria o qa-gate) |
| `npm run test:watch` | Vitest em modo watch, para desenvolver |
| `npm run lint` | ESLint, zero warnings (bloqueia `dangerouslySetInnerHTML`/`innerHTML` e `any`) |
| `npm run typecheck` | `tsc` nos dois projetos (app e node) — inclui o contrato das fixtures |
| `npm run build` | typecheck + build de produção |
| `npm run capture-fixtures` | recaptura as fixtures da API local (abaixo) |

## Fixtures de teste: respostas REAIS da API

Os testes nunca usam JSON digitado à mão. `src/test/fixtures/*.json` são respostas capturadas
do backend local por `scripts/capture-fixtures.mjs` (envelope com requisição, status, headers,
data e o corpo intacto). `src/test/fixtures/index.ts` anota cada uma com o tipo de
`src/api/types.ts`: se um DTO do backend mudar e as fixtures forem recapturadas, o
`npm run typecheck` acusa a divergência.

Estados da UI sem exemplo real no catálogo **não** ganham teste com dado inventado: ficam
listados em `src/test/fixtures/coverage.md` (gerado pelo script).

Recapturar (backend rodando em 8081):

```bash
node scripts/capture-fixtures.mjs basic      # sem efeito colateral: busca, 404, 400
node scripts/capture-fixtures.mjs main       # fluxo completo; importa objetos reais do SIMBAD
                                             # (exige que Betelgeuse ainda NAO esteja no banco)
# falha e fila cheia: reinicie o backend com Sesame numa porta morta e fila de 1
SERVER_PORT=8081 GALAXY_SIMBAD_SESAME_URL=http://127.0.0.1:9/ GALAXY_IMPORTS_QUEUE_CAPACITY=1 ./mvnw spring-boot:run
node scripts/capture-fixtures.mjs degraded   # failed (SOURCE_UNAVAILABLE) e 429; nao toca o CDS
node scripts/capture-fixtures.mjs coverage   # so regenera coverage.md
```

## Estrutura

```
src/
  api/         tipos (espelho dos DTOs Java), fetch, endpoints, hooks de query
  domain/      funcoes puras: formatacao, selos, conflito, distancia (sem React, sem fetch)
  components/  layout e primitivas genericas (ExternalLink e o UNICO <a> externo)
  features/    home, search, object, category
  pages/       rotas
  test/        setup do MSW, handlers, fixtures, testes de fluxo
```

## qa-gate

O `.claude/qa-gate.config.json` da raiz roda `npm test` e `npm run lint`/`typecheck` aqui antes
dos testes do backend: rode `npm ci` em `frontend/` uma vez e mantenha o Docker aberto.
