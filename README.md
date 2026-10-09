# Galaxy Explorer

Explorador web do universo feito **só com dados astronômicos reais e rastreáveis**.
Você busca um objeto (ex.: `M31`, `Vega`, `NGC 5194`) e vê suas propriedades, cada
valor com a fonte, a referência bibliográfica e a incerteza — e imagens reais com
crédito e licença.

Versão atual: **0.1.0-beta** (ver [CHANGELOG.md](CHANGELOG.md)).

## Princípio: dados reais, com proveniência

- **Nada é inventado.** Todo valor exibido vem de uma fonte científica (hoje, SIMBAD);
  se a fonte não tem o dado, a interface mostra a ausência e o motivo — nunca um
  valor de preenchimento.
- **Proveniência por valor.** Cada medição guarda fonte, identificador na fonte,
  referência (bibcode ADS), incerteza e o payload cru de onde saiu (auditável).
- **Conflito visível.** Quando fontes divergem além de 3σ, a interface mostra o selo
  "Fontes divergem" e as alternativas, em vez de escolher em silêncio.
- **Imagens só reais e creditadas.** Recortes de surveys (hips2fits) e fotos do
  ESA/Hubble e ESA/Webb, com crédito literal e licença; nenhuma ilustração ou imagem
  gerada por IA é apresentada como foto. O navegador nunca acessa sites de terceiros:
  as imagens são baixadas na ingestão e servidas pela própria API.

Arquitetura, modelo de dados e decisões: [docs/00-fase0-arquitetura.md](docs/00-fase0-arquitetura.md).

## Pré-requisitos

- **Para rodar a beta:** Docker com Compose v2 (Docker Desktop no Windows/macOS) e
  internet na primeira subida (carga inicial dos dados).
- **Para desenvolver:** além do Docker, Java 17 (JDK) e Node 24. O Maven vem pelo
  wrapper (`mvnw`).

## Como rodar a beta

```bash
git clone <url-do-repositorio> galaxy-explorer
cd galaxy-explorer
cp .env.example .env          # opcional: sem .env valem os defaults
docker compose up -d --build
```

Abra **http://127.0.0.1:8088/** (só nesta máquina: a porta é publicada em
`127.0.0.1`; para mudar, `GALAXY_HTTP_PORT` no `.env`).

O que sobe:

| Serviço | Papel | Exposto no host |
|---|---|---|
| `postgres` | PostgreSQL 17 (volume `galaxy-pgdata`) | `127.0.0.1:5433` |
| `backend` | API Spring Boot (porta 8080 **só na rede interna**) | não |
| `seed` | carga inicial; roda uma vez e sai | não |
| `frontend` | nginx: app React + proxy `/api/` → backend (mesma origem) | `127.0.0.1:8088` |

A API não tem autenticação, por isso o backend não tem porta publicada: tudo passa
pelo nginx, que também envia CSP de produção, `X-Content-Type-Options: nosniff` e
`Referrer-Policy`.

Comandos úteis:

```bash
docker compose ps                    # estado (backend/frontend devem ficar "healthy")
docker compose logs -f seed          # acompanhar a carga inicial
docker compose down                  # parar (mantém banco e imagens)
docker compose down -v               # parar e APAGAR banco e imagens (próxima subida recarrega)
```

### Primeira carga de dados

Na primeira subida, o serviço `seed` (script `backend/docker/seed.sh`, que reaproveita
os comandos `seed` e `images` do backend) faz:

1. **Objetos:** os 110 objetos Messier, resolvidos pelo CDS Sesame e ingeridos do
   SIMBAD (cerca de 1 minuto).
2. **Imagens:** recorte hips2fits (PanSTARRS ou DSS2) para cada objeto e busca de fotos
   ESA/Hubble e ESA/Webb verificadas pelos metadados AVM (cerca de 20 minutos — os serviços são consultados a
   no máximo 1 requisição/s, por respeito aos limites deles).

Total medido numa subida limpa: cerca de 22 minutos (objetos 47 s, recortes 10 min,
ESA/Hubble 9 min, ESA/Webb 2,5 min), resultando em 110 objetos, 110 recortes, 106 fotos
ESA/Hubble e 14 ESA/Webb.

O app já funciona durante a carga: os objetos e imagens vão aparecendo.

- **Idempotente, com estado no banco:** o seed decide o que fazer olhando o próprio
  banco, nunca um arquivo. Passo 1: só pede ao SIMBAD os Messier que ainda não existem
  no banco. Passo 2: não faz nada se a última passada de cada fonte de imagem (recorte,
  ESA/Hubble, ESA/Webb) terminou `ok`. Com tudo completo, as subidas seguintes não fazem
  nenhuma requisição externa. Depois de `docker compose down -v` o banco é novo e a
  carga roda de novo, mesmo que as imagens estejam num diretório do host
  (`GALAXY_IMAGES_DIR`).
- **Se a rede falhar:** qualquer objeto que falhe faz o passo sair com erro (a passada
  fica `partial`/`failed` no banco) e nada do que já entrou se perde. Basta subir de
  novo (`docker compose up -d`) ou rodar `docker compose run --rm seed`: só os objetos
  ausentes e as imagens que faltam são consultados de novo.
- **Forçar nova passada de imagens** (ex.: depois de editar `images/curation.csv`):
  `docker compose run --rm -e GALAXY_SEED_FORCE=1 seed` (continua incremental).

Objetos fora do Messier (ex.: `Vega`, `Betelgeuse`) são importados sob demanda pela
própria busca, também do SIMBAD.

## Modo desenvolvimento

O fluxo de desenvolvimento não mudou: só o banco vem do compose; backend e frontend
rodam locais, com recarga rápida.

Um terminal por comando, cada um aberto **na raiz do repositório** (o backend e o
frontend ficam rodando e ocupam o terminal):

```bash
# terminal 1 — só o banco (127.0.0.1:5433)
docker compose up -d postgres

# terminal 2 — backend em 127.0.0.1:8081
cd backend && SERVER_PORT=8081 ./mvnw spring-boot:run
# PowerShell: cd backend; $env:SERVER_PORT=8081; .\mvnw.cmd spring-boot:run

# terminal 3 — frontend em http://127.0.0.1:5173
cd frontend && npm ci && npm run dev
```

Carga de dados no modo dev (mesmos comandos que o `seed` usa; num terminal a partir
da raiz):

```bash
cd backend
./mvnw spring-boot:run "-Dspring-boot.run.profiles=seed"
./mvnw spring-boot:run "-Dspring-boot.run.profiles=images" \
  "-Dspring-boot.run.arguments=--galaxy.images.run.sources=CUTOUT,ESA_HUBBLE,ESA_WEBB --galaxy.images.run.objects=all"
```

Detalhes do frontend (scripts, fixtures reais de teste): [frontend/README.md](frontend/README.md).

### Beta e desenvolvimento no mesmo banco

No mesmo diretório, a beta e o modo dev usam o **mesmo** Postgres (volume
`galaxy-pgdata`). As imagens, porém, ficam em lugares diferentes: no dev, em
`backend/data/images`; na beta, no volume `galaxy-images`. Se o banco já foi populado
pelo backend local, a beta encontraria registros de imagem sem o arquivo. Nesse caso,
coloque no `.env`:

```bash
GALAXY_IMAGES_DIR=./backend/data/images
```

(Numa máquina limpa não é preciso: a beta cria e popula o próprio volume.) Para uma
beta isolada do banco de dev (pode rodar junto com a outra), use outro nome de
projeto e outras portas de banco e HTTP — depois abra http://127.0.0.1:8089:

```bash
# bash (Git Bash, Linux, macOS)
GALAXY_HTTP_PORT=8089 POSTGRES_PORT=5434 docker compose -p galaxy-beta up -d --build
```

```powershell
# PowerShell (as variáveis valem para a sessão; ou ponha as duas no .env)
$env:GALAXY_HTTP_PORT="8089"; $env:POSTGRES_PORT="5434"; docker compose -p galaxy-beta up -d --build
```

## Testes (qa-gate)

O repositório usa o `qa-gate` (`.claude/hooks/qa-gate.py`, configurado em
`.claude/qa-gate.config.json`): nenhum commit passa sem a suíte e o lint verdes.
Os comandos (no Windows, pelo `cmd.exe`) — cada linha é um comando separado,
rodado a partir da raiz:

```bat
cd frontend && npm test && cd ..\backend && .\mvnw.cmd -q -B test
cd frontend && npm run lint && npm run typecheck
```

Os testes de integração do backend usam **Testcontainers** (precisam do Docker
rodando). Todos os testes trabalham sobre respostas reais capturadas das fontes e da
API — nenhum JSON digitado à mão. O build das imagens Docker não roda testes (não há
Docker dentro do build); eles são garantidos pelo qa-gate antes de cada commit.

## Estrutura do repositório

```
backend/            API Spring Boot 4 / Java 17 (JdbcClient + Flyway)
  src/main/java/dev/galaxyexplorer/
    api/            controllers REST (/api/v1/search, /objects, /categories, /imports, /images)
    catalog/        modelo e consultas (objetos, medições, identificadores, imagens)
    ingestion/      conectores (Sesame, SIMBAD TAP, hips2fits, ESA), pipeline, seed
    media/          armazenamento das imagens (endereçadas por SHA-256)
  src/main/resources/
    db/migration/   migrações Flyway V1..V8
    images/curation.csv  curadoria manual de imagens ESA
  docker/seed.sh    carga inicial do serviço seed
  Dockerfile
frontend/           React 19 + TypeScript + Vite 8
  src/              api/, domain/, components/, features/, pages/
  nginx.conf        servidor da beta (SPA + proxy /api/ + headers)
  Dockerfile
docs/               arquitetura (Fase 0)
docker-compose.yml  beta completa e banco de desenvolvimento
```

## Fontes de dados, créditos e licenças

| Fonte | Uso | Termos |
|---|---|---|
| **SIMBAD** (CDS, Estrasburgo) | posições, tipos, medições, referências | [cds.unistra.fr/legals](https://cds.unistra.fr/legals/) |
| **CDS Sesame** | resolução de nomes (`M31` → objeto SIMBAD) | [cds.unistra.fr/legals](https://cds.unistra.fr/legals/) |
| **CDS hips2fits** + **DSS2 colorido** / **Pan-STARRS DR1** | recortes de survey por coordenada | dois níveis: o **HiPS** (o mosaico gerado pelo CDS) é [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) (CNRS/Unistra) — é essa licença que aparece junto do recorte; os **dados do survey** têm termos próprios: [DSS (STScI)](http://archive.stsci.edu/dss/copyright.html), [Pan-STARRS1](http://panstarrs.stsci.edu/) |
| **ESA/Hubble**, **ESA/Webb** | fotos de destaque | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), crédito literal obrigatório — [esahubble.org/copyright](https://esahubble.org/copyright/), [esawebb.org/copyright](https://esawebb.org/copyright/) |

Agradecimentos exigidos pelas fontes (também exibidos na página de cada objeto):

> This research has made use of the SIMBAD database, operated at CDS, Strasbourg,
> France. 2000, A&AS, 143, 9, "The SIMBAD astronomical database", Wenger et al.

> This research made use of hips2fits, a service provided by CDS.

Créditos dos surveys, como declarados pelo CDS: *Digitized Sky Survey - STScI/NASA,
Colored & Healpixed by CDS* (DSS2) e *PS1 Science Consortium* (Pan-STARRS).

Cada foto ESA/Hubble ou ESA/Webb é exibida com o crédito literal dos metadados AVM
(ex.: "ESA/Webb, NASA & CSA, …"), a licença CC BY 4.0 e o link para a página original.

O NASA Exoplanet Archive já está cadastrado como fonte, mas ainda não é consultado
nesta beta.

## Limitações conhecidas da beta

- Catálogo inicial: 110 objetos Messier; outros objetos entram sob demanda pela busca
  (uma consulta ao SIMBAD por vez, fila limitada).
- Uma fonte de medições só (SIMBAD); exoplanetas (NASA Exoplanet Archive) e outras
  fontes ficam para as próximas fases.
- Sem autenticação: a beta é para uso local (`127.0.0.1`), não para exposição em rede.
- Primeira carga exige internet e leva cerca de 20 a 25 minutos; sem rede, o app sobe vazio
  e a carga é retomada na próxima subida.
- Alguns objetos ficam só com o recorte de survey (sem foto ESA verificável) e alguns
  campos muito grandes usam o recorte como destaque (ex.: M31, M42).
- Sem visualização 3D ainda (planejada para fase seguinte).
