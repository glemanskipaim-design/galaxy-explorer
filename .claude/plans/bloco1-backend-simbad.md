---
agent: musashi
timestamp: 2026-10-05T00:00:00Z   # data do ambiente; hora nao obtida (musashi nao tem Bash)
target_files:
  - docker-compose.yml
  - .env.example
  - backend/ (projeto gerado via start.spring.io: pom.xml, mvnw, mvnw.cmd, .mvn/)
  - backend/src/main/resources/application.yml
  - backend/src/main/resources/application-seed.yml
  - backend/src/main/resources/db/migration/V1__core_schema.sql
  - backend/src/main/resources/db/migration/V2__reference_data.sql
  - backend/src/main/resources/db/migration/V3__import_job.sql
  - backend/src/main/java/dev/galaxyexplorer/** (config, catalog, ingestion, api)
  - backend/src/test/java/dev/galaxyexplorer/**
  - backend/src/test/resources/fixtures/simbad/** (capturadas ao vivo, nunca digitadas)
---

# Bloco 1 — Backend + PostgreSQL + conector SIMBAD + seed Messier

Fonte de verdade da arquitetura: `docs/00-fase0-arquitetura.md` (aprovado).
Este plano **não reabre** decisões de lá. Onde precisei preencher uma lacuna
que o documento não cobria, está marcado como **[DECISÃO NOVA]** com o
motivo, para o usuário poder vetar antes da implementação.

Convenções gerais para o `sasaki`:

- Java **17** (JDK local). Consequência prática: **não** usar `switch` com
  pattern matching nem record patterns (Java 21), nem `List.getFirst()`
  (Java 21), nem virtual threads. `instanceof` com pattern (`x instanceof Foo f`)
  e `sealed` + `record` são Java 17 e podem ser usados.
- Spring Boot **4.1.1** usa **Jackson 3** (pacote `tools.jackson.*`, não
  `com.fasterxml.jackson.databind`). As anotações (`com.fasterxml.jackson.annotation`)
  continuam no pacote antigo. Não adicionar `jackson-databind` 2 à mão.
- Acesso a dados: **só `JdbcClient`** + SQL explícito. Nenhuma dependência JPA.
- Transações: usar `TransactionTemplate` explícito no pipeline (não
  `@Transactional` em método chamado internamente — o proxy do Spring não
  intercepta auto-invocação, e isso é uma armadilha clássica). Repositórios
  não abrem transação própria; quem orquestra decide a fronteira.
- Tempo: injetar um `java.time.Clock` (bean UTC) em tudo que grava
  `retrieved_at`/`started_at`, para testes determinísticos.
- Formato de erro da API: o da skill `api-development`
  (`{"error":{"code","message","details"}}`), com catálogo central de `code`.
- Pacote raiz: `dev.galaxyexplorer`.

---

## Estrutura proposta

### 0. Por que essa divisão (resumo didático)

Direção de dependência — **a regra mais importante do plano**:

```
api ──► catalog
api ──► ingestion.jobs ──► ingestion.pipeline ──► catalog
                                     │
                                     └──► ingestion.connector (contrato + modelo canônico)
                                                   ▲
                         ingestion.connector.simbad ┘  ──► ingestion.connector.tap
```

- `catalog` é o **domínio**: objeto, identificador, medição, política de
  preferência, valores derivados. Não conhece nenhuma fonte externa nem o
  pacote `ingestion`. Isso é o que permite, no futuro, adicionar NED/JPL sem
  tocar no domínio.
- `ingestion.connector` define o **contrato** (`SourceConnector`) e o
  **modelo canônico** (`NormalizedObject` etc.) que todo conector produz.
  Não importa `catalog`, com uma exceção deliberada: os enums de vocabulário
  `catalog.SourceCode` e `catalog.Epistemic` (são o "idioma comum" do sistema).
- `ingestion.connector.simbad` é a única coisa que sabe o que é `otype`,
  `plx_value`, ADQL do SIMBAD. Se o SIMBAD mudar, a mudança fica aqui.
- `ingestion.connector.tap` **[DECISÃO NOVA]**: utilitário genérico de TAP
  (POST ADQL, parse do JSON `metadata/data`). Motivo: SIMBAD e NASA Exoplanet
  Archive (as duas fontes do MVP) falam o mesmo protocolo TAP; sem isso o
  Bloco 2 copiaria o cliente HTTP. Cada fonte continua com a **sua** instância
  (seu rate limiter, seu circuit breaker) — o código é compartilhado, o
  estado não.
- `ingestion.pipeline` orquestra os passos da seção 8 do doc e é genérico
  (recebe qualquer `SourceConnector`).
- `ingestion.jobs` (listado no doc, seção 5) é a importação sob demanda.
- `ingestion.seed` é o comando de carga inicial.
- `api` só traduz HTTP ⇄ serviços; não tem regra de negócio.

### 1. Árvore de arquivos

```
galaxy-explorer/
├── docker-compose.yml
├── .env.example
└── backend/
    ├── pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/…          (gerados pelo start.spring.io)
    └── src/
        ├── main/
        │   ├── java/dev/galaxyexplorer/
        │   │   ├── GalaxyExplorerApplication.java        (gerado)
        │   │   ├── config/
        │   │   │   └── ClockConfig.java
        │   │   ├── catalog/
        │   │   │   ├── SourceCode.java
        │   │   │   ├── Epistemic.java
        │   │   │   ├── source/        SourceRepository.java, SourceRow.java
        │   │   │   ├── identifier/    IdentifierNormalizer.java, IdentifierRepository.java,
        │   │   │   │                  IdentifierRow.java, IdentifierMatch.java
        │   │   │   ├── category/      CategoryResolver.java, CategoryDecision.java,
        │   │   │   │                  TypeMappingRule.java, CategoryRepository.java, CategoryRow.java
        │   │   │   ├── object/        CelestialObjectRepository.java, ObjectRow.java,
        │   │   │   │                  ObjectSourceTypeRepository.java, SourceTypeRow.java,
        │   │   │   │                  SlugFactory.java, DisplayNameRule.java,
        │   │   │   │                  AmbiguousLocalMatchException.java
        │   │   │   ├── position/      PositionRepository.java, PositionWrite.java, PositionRow.java
        │   │   │   ├── reference/     BibReferenceRepository.java
        │   │   │   ├── measurement/   MeasurementRepository.java, MeasurementWrite.java,
        │   │   │   │                  MeasurementRow.java, MeasurementKey.java,
        │   │   │   │                  PreferenceCandidate.java, PriorityRule.java,
        │   │   │   │                  PriorityRuleRepository.java, PreferencePolicy.java,
        │   │   │   │                  PreferenceService.java
        │   │   │   ├── derived/       DistanceFromParallax.java, DistanceComputation.java,
        │   │   │   │                  DerivedValueService.java
        │   │   │   ├── write/         CatalogWriter.java, ObjectWrite.java,
        │   │   │   │                  IdentifierWrite.java, WriteResult.java
        │   │   │   └── read/          SearchService.java, SearchHit.java, MatchType.java,
        │   │   │                      ObjectQueryService.java, ObjectDetail.java,
        │   │   │                      PropertyView.java, MeasurementView.java, AbsenceReason.java
        │   │   ├── ingestion/
        │   │   │   ├── connector/     SourceConnector.java, RawPayload.java, SourceObjectRef.java,
        │   │   │   │                  ResolvedName.java, UnresolvedQuery.java, Resolution.java,
        │   │   │   │                  SchemaCheck.java, FetchedBatch.java,
        │   │   │   │                  NormalizationOutcome.java, NormalizedObject.java,
        │   │   │   │                  NormalizedIdentifier.java, NormalizedPosition.java,
        │   │   │   │                  NormalizedMeasurement.java,
        │   │   │   │                  SourceUnavailableException.java, SourceQueryException.java
        │   │   │   ├── connector/tap/ TapClient.java, HttpTapClient.java, TapResult.java,
        │   │   │   │                  TapColumn.java, TapRow.java, TapJsonParser.java, AdqlLiterals.java
        │   │   │   ├── connector/simbad/
        │   │   │   │                  SimbadProperties.java, SimbadConfig.java, SimbadConnector.java,
        │   │   │   │                  SimbadQueries.java, SimbadIdentifierCandidates.java,
        │   │   │   │                  SimbadSchemaExpectations.java, SimbadMapper.java,
        │   │   │   │                  SimbadValidator.java, SimbadUnits.java
        │   │   │   ├── pipeline/      IngestionPipeline.java, IngestionProperties.java,
        │   │   │   │                  IngestionRequest.java, IngestionReport.java, ObjectOutcome.java,
        │   │   │   │                  RunTrigger.java, RunStatus.java,
        │   │   │   │                  IngestionRunRepository.java, RawPayloadRepository.java,
        │   │   │   │                  NormalizedToCatalog.java
        │   │   │   ├── jobs/          ImportJobService.java, ImportJobWorker.java,
        │   │   │   │                  ImportJobRepository.java, ImportJob.java,
        │   │   │   │                  ImportJobStatus.java, ImportExecutorConfig.java,
        │   │   │   │                  ImportQueueFullException.java
        │   │   │   └── seed/          MessierDesignations.java, MessierSeedRunner.java
        │   │   └── api/
        │   │       ├── error/         ApiError.java, ApiErrorCode.java, ApiException.java,
        │   │       │                  ApiExceptionHandler.java
        │   │       ├── search/        SearchController.java, SearchResponse.java, SearchHitDto.java
        │   │       ├── object/        ObjectController.java, ObjectDetailResponse.java,
        │   │       │                  ObjectDetailMapper.java
        │   │       └── imports/       ImportController.java, CreateImportRequest.java,
        │   │                          ImportJobResponse.java
        │   └── resources/
        │       ├── application.yml
        │       ├── application-seed.yml
        │       └── db/migration/
        │           ├── V1__core_schema.sql
        │           ├── V2__reference_data.sql
        │           └── V3__import_job.sql
        └── test/
            ├── java/dev/galaxyexplorer/
            │   ├── TestcontainersConfiguration.java        (gerado — manter)
            │   ├── TestGalaxyExplorerApplication.java      (gerado — manter)
            │   ├── GalaxyExplorerApplicationTests.java     (gerado — manter)
            │   ├── support/        Fixtures.java, FixtureTapClient.java
            │   ├── catalog/        MigrationIntegrationTest, ReferenceDataIntegrationTest,
            │   │                   MeasurementRepositoryIntegrationTest, CatalogWriterIntegrationTest,
            │   │                   identifier/IdentifierNormalizerTest,
            │   │                   category/CategoryResolverTest,
            │   │                   object/SlugFactoryTest, object/DisplayNameRuleTest,
            │   │                   derived/DistanceFromParallaxTest,
            │   │                   measurement/PreferencePolicyTest
            │   ├── ingestion/      connector/tap/TapJsonParserTest, connector/tap/AdqlLiteralsTest,
            │   │                   connector/tap/HttpTapClientTest,
            │   │                   connector/simbad/SimbadQueriesTest,
            │   │                   connector/simbad/SimbadIdentifierCandidatesTest,
            │   │                   connector/simbad/SimbadSchemaExpectationsTest,
            │   │                   connector/simbad/SimbadMapperTest,
            │   │                   pipeline/IngestionPipelineIntegrationTest,
            │   │                   jobs/ImportJobServiceIntegrationTest,
            │   │                   seed/MessierDesignationsTest
            │   └── api/            SearchApiIntegrationTest, ObjectApiIntegrationTest,
            │                       ImportApiIntegrationTest
            └── resources/fixtures/simbad/
                ├── README.md                     (data/hora UTC da captura, endpoint, comando curl usado)
                ├── <nome>.adql + <nome>.json     (pares: consulta exata + resposta real)
                └── …                             (lista na seção I abaixo)
```

### 2. Infraestrutura

**`docker-compose.yml`** (raiz)
- Serviço `postgres`: imagem `postgres:17` (mesma major usada no Testcontainers).
- `POSTGRES_DB=galaxy`, `POSTGRES_USER=galaxy`,
  `POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-galaxy_dev}`.
- Porta publicada **só em loopback**: `127.0.0.1:${POSTGRES_PORT:-5432}:5432`.
  Motivo: banco de desenvolvimento não deve ficar exposto na rede local; e a
  porta parametrizada evita conflito com um PostgreSQL local (ver Riscos R3).
- Volume nomeado `galaxy-pgdata` em `/var/lib/postgresql/data` (caminho
  correto para a imagem 17; a 18 mudou o caminho — não trocar de major sem
  rever isso).
- `healthcheck`: `pg_isready -U galaxy -d galaxy`, intervalo 5s, timeout 3s,
  10 tentativas, `start_period` 10s.
- Blocos `backend` e `frontend` **comentados**, com nota "Bloco futuro"; o
  backend futuro terá `depends_on: postgres: condition: service_healthy`.

**`.env.example`**: `POSTGRES_PASSWORD=` e `POSTGRES_PORT=5432` com comentário.
`.env` já está no `.gitignore`.

**`application.yml`**
- `spring.datasource.url=jdbc:postgresql://localhost:${GALAXY_DB_PORT:5432}/galaxy`,
  usuário `galaxy`, senha `${GALAXY_DB_PASSWORD:galaxy_dev}` (default só de
  desenvolvimento, coerente com o compose).
- `spring.flyway` com defaults (`classpath:db/migration`).
- `management.endpoints.web.exposure.include=health,info` (nada além disso).
- `galaxy.simbad.*` (ver `SimbadProperties`).
- `galaxy.ingestion.batch-size=10` (ver `IngestionProperties`).
- `galaxy.imports.queue-capacity=20`.

**`application-seed.yml`**: `spring.main.web-application-type=none` (o seed
roda, imprime o relatório e sai — não sobe servidor HTTP).

### 3. Dependências (pom)

Gerar com start.spring.io (Boot 4.1.1, Java 17, Maven, `groupId=dev.galaxyexplorer`,
`artifactId=galaxy-explorer-backend`, `name=galaxy-explorer`,
`packageName=dev.galaxyexplorer`, `baseDir=backend`), ids:
`web,jdbc,flyway,postgresql,validation,actuator,testcontainers`.

- **Aceitar exatamente os artefatos que o start.spring.io gerar** (no Boot 4
  os starters foram modularizados: Flyway exige `spring-boot-starter-flyway`,
  e cada starter ganha um `*-test` correspondente; Testcontainers 2.x renomeou
  módulos para `testcontainers-postgresql`/`testcontainers-junit-jupiter`).
  Não "corrigir" nomes de artefato de memória.
- Conferir que `flyway-database-postgresql` veio; se não, adicionar.
- **Resilience4j: NÃO marcar a opção "Resilience4J" do start.spring.io**
  (ela traz Spring Cloud CircuitBreaker, que exige um release train do Spring
  Cloud compatível com Boot 4.1 — dependência pesada e possivelmente
  incompatível). **[DECISÃO NOVA]** Adicionar à mão só os módulos core:
  `io.github.resilience4j:resilience4j-ratelimiter`, `resilience4j-retry`,
  `resilience4j-circuitbreaker`, versão via propriedade `resilience4j.version`
  = última 2.x estável no Maven Central (verificar com
  `curl.exe "https://search.maven.org/solrsearch/select?q=g:io.github.resilience4j+AND+a:resilience4j-retry&rows=1&wt=json"`).
  Motivo: usados programaticamente (decorators), sem auto-configuração — você
  vê na mão a ordem retry → circuit breaker → rate limiter, o que é didático,
  e não dependemos de um starter que talvez ainda não suporte Boot 4.
- Cliente HTTP: `RestClient` do `spring-web`, construído manualmente em
  `SimbadConfig` (`RestClient.builder()`), sem depender do bean
  auto-configurado `RestClient.Builder` (no Boot 4 ele vive em módulo próprio).

### 4. Migrações Flyway

**V1__core_schema.sql** — o SQL da seção 6 do doc, **copiado literalmente**
(todas as tabelas, inclusive `orbit_solution` e `object_image`, que o Bloco 1
não usa — o esquema aprovado é um todo). Única adição no topo:
`CREATE EXTENSION IF NOT EXISTS pg_trgm;` (exigido pelo índice `gin_trgm_ops`).
**Não** criar `pgcrypto`: `gen_random_uuid()` é nativo desde o PostgreSQL 13.

**V2__reference_data.sql** — dados de referência. Regras:
- Nunca fixar ids numéricos; referenciar por `code` via subselect
  (`(SELECT id FROM source WHERE code='SIMBAD')`).
- Todo `source_type` SIMBAD inserido em `source_type_mapping` **tem que
  existir** na fixture real `otypedef_all.json` (há teste para isso).
- URLs de `source` verificadas com `curl.exe -sI <url>` (status 200/3xx)
  antes de gravar; textos de agradecimento copiados **literalmente** da
  página oficial da fonte, nunca parafraseados.

`source`:

| code | name | homepage_url | terms_url | acknowledgment |
|---|---|---|---|---|
| SIMBAD | SIMBAD Astronomical Database (CDS, Strasbourg) | https://simbad.cds.unistra.fr/simbad/ | página de termos/licença do CDS (localizar e verificar a URL vigente ao vivo) | texto oficial do CDS — esperado algo como "This research has made use of the SIMBAD database, operated at CDS, Strasbourg, France", com a citação Wenger et al. 2000, A&AS 143, 9; **copiar o texto exato da página do CDS** |
| NASA_EXO | NASA Exoplanet Archive | https://exoplanetarchive.ipac.caltech.edu/ | https://exoplanetarchive.ipac.caltech.edu/docs/acknowledge.html (verificar) | texto oficial da página acknowledge.html, literal |

`object_category` (hierarquia; `code` em inglês snake_case, nomes pt/en):

| code | parent | name_pt | name_en |
|---|---|---|---|
| star | — | Estrela | Star |
| galaxy | — | Galáxia | Galaxy |
| galaxy_spiral | galaxy | Galáxia espiral | Spiral galaxy |
| galaxy_elliptical | galaxy | Galáxia elíptica | Elliptical galaxy |
| galaxy_lenticular | galaxy | Galáxia lenticular | Lenticular galaxy |
| galaxy_irregular | galaxy | Galáxia irregular | Irregular galaxy |
| nebula | — | Nebulosa | Nebula |
| nebula_planetary | nebula | Nebulosa planetária | Planetary nebula |
| nebula_emission | nebula | Nebulosa de emissão | Emission nebula |
| nebula_reflection | nebula | Nebulosa de reflexão | Reflection nebula |
| nebula_snr | nebula | Remanescente de supernova | Supernova remnant |
| cluster | — | Aglomerado estelar | Star cluster |
| cluster_open | cluster | Aglomerado aberto | Open cluster |
| cluster_globular | cluster | Aglomerado globular | Globular cluster |
| agn | — | Núcleo galáctico ativo (AGN) | Active galactic nucleus |
| quasar | agn | Quasar | Quasar |
| black_hole | — | Buraco negro | Black hole |
| exoplanet | — | Exoplaneta | Exoplanet |
| x_ray_source | — | Fonte de raios X | X-ray source |
| other | — | Outro | Other |

(`agn > quasar`: você listou "AGN/quasar" como um item; quasar é um tipo de
AGN, então virou filho — não muda nada para o MVP e evita duas categorias
irmãs que se sobrepõem.)

`property_definition`:

| code | label_pt | canonical_unit | value_kind | display_group |
|---|---|---|---|---|
| distance | Distância | pc | numeric | astrometry |
| parallax | Paralaxe | mas | numeric | astrometry |
| redshift | Redshift (z) | NULL | numeric | kinematics |
| radial_velocity | Velocidade radial | km/s | numeric | kinematics |
| app_mag | Magnitude aparente | mag | numeric | photometry |
| spectral_type | Tipo espectral | NULL | text | classification |
| morph_type | Tipo morfológico | NULL | text | classification |
| ang_size_major | Tamanho angular (eixo maior) | arcmin | numeric | morphology |
| ang_size_minor | Tamanho angular (eixo menor) | arcmin | numeric | morphology |

`app_mag` usa `qualifier` = banda (`V`, `B`, `G`…), exatamente como o SIMBAD
escreve em `flux.filter` (case-sensitive: `g` SDSS ≠ `G` Gaia).
`morph_type` aparece em dois lugares de propósito: em `object_source_type`
(tipo bruto usado para classificar) e como `measurement` (valor exibido, com
proveniência e bibcode próprios).

`source_type_mapping` (fonte SIMBAD). **Regras de morfologia**: dialeto de
regex **Java** (`Pattern.find()`), sempre ancoradas em `^`, aplicadas ao
`morph_type` com `trim()`. Prefixo opcional de anel externo `(R)`/`(R')` é
aceito (M64, M77, M94 usam).

| nome da regra | morph_pattern (descrição) | categoria | priority |
|---|---|---|---|
| lenticular | `^(\(R'?\))?S(A\|B\|AB)?(\([^)]*\))?0` — S0, SA0, SB(s)0… | galaxy_lenticular | 30 |
| espiral | `^(\(R'?\))?S(A\|B\|AB)?(\([^)]*\))?[abcdm]` — Sb, SA(s)b, SAB(rs)cd… | galaxy_spiral | 20 |
| elíptica | `^[cd]?E` — E2, cE2, dE5, E+0-1 | galaxy_elliptical | 20 |
| irregular | `^d?I` — Irr, IBm, I0, Im | galaxy_irregular | 20 |
| genérica | `''` (qualquer) | galaxy (ou agn/quasar, ver abaixo) | 0 |

(Na tabela, `\|` é só escape do Markdown; no SQL é `|`.)

Essas 4 regras de morfologia + a genérica são inseridas para **cada otype
de galáxia/AGN que aparecer de fato** na fixture `messier_types.json`
(esperado algo como `G`, `GiG`, `GiC`, `BiC`, `SBG`, `H2G`, `rG`, `AGN`,
`Sy1`, `Sy2`, `SyG`, `LIN` — **confirmar no dado real**). Sugestão de SQL
declarativo: `INSERT … SELECT` de um `VALUES` de otypes `CROSS JOIN` um
`VALUES` de regras. A regra genérica `''` aponta para `galaxy` nos otypes de
galáxia e para `agn` nos otypes de AGN (`AGN`, `Sy1`, `Sy2`, `SyG`, `LIN`);
`QSO` → `quasar`. Assim M31 (`AGN` + `SA(s)b`) vira `galaxy_spiral` com
`category_basis = "SIMBAD otype=AGN + morph_type=SA(s)b → regra espiral"`, e
um AGN sem morfologia continua `agn`.

Demais otypes (morph_pattern `''`, priority 0) — **incluir só os que existem
no otypedef real**, e no mínimo todos os que aparecem em `messier_types.json`
e nas fixtures de amostra:

| otype (confirmar no otypedef) | categoria |
|---|---|
| `GlC` | cluster_globular |
| `OpC` | cluster_open |
| `Cl*`, `As*`, `C?*` (se existirem) | cluster |
| `PN` | nebula_planetary |
| `HII` | nebula_emission |
| `RNe` | nebula_reflection |
| `SNR` | nebula_snr |
| `ISM`, `Cld`, `GNe`, `DNe`, `MoC` (se existirem) | nebula |
| `*`, `**`, `s*r` (Betelgeuse, verificado), outros otypes de estrela que surgirem nas fixtures | star |
| `X` (Sgr A*, verificado) | x_ray_source |
| `BH` (se existir) | black_hole |
| `Pl` (se existir) | exoplanet |

Sgr A* fica `x_ray_source`, **não** `black_hole` — exatamente a descoberta 2
do doc (buraco negro é interpretação, não tipo de catálogo). Otype sem regra
→ categoria `other`, `category_basis = "SIMBAD otype=<x> sem regra de
mapeamento"` (não é erro; o relatório do seed lista esses casos).

`property_source_priority`: **vazio** no Bloco 1 (só há SIMBAD; as regras da
seção 7 envolvem NED/Gaia, que são fases posteriores).

**V3__import_job.sql** — **[DECISÃO NOVA]**. O esquema da seção 6 não tem
onde guardar "o termo que o usuário pediu" nem o resultado de um job;
`ingestion_run` registra uma execução contra uma fonte, não um pedido do
usuário. Ficar em memória perderia o status a cada restart (e o frontend faz
polling). Por isso uma tabela nova, em migração separada para que V1 continue
sendo literalmente a seção 6:

| coluna | tipo / regra |
|---|---|
| id | bigserial PK |
| query | text NOT NULL (como o usuário digitou, já com trim) |
| normalized_query | text NOT NULL (via `IdentifierNormalizer`) |
| status | text NOT NULL CHECK IN ('queued','running','succeeded','not_found','failed') |
| message | text (motivo legível: "Objeto não encontrado em SIMBAD", erro resumido…) |
| run_id | bigint REFERENCES ingestion_run (NULL se nem chegou a rodar) |
| result_object_ids | uuid[] NOT NULL DEFAULT '{}' |
| created_at | timestamptz NOT NULL DEFAULT now() |
| started_at, finished_at | timestamptz |

Índice único parcial `one_active_import` em `(normalized_query)` WHERE
`status IN ('queued','running')` — o banco garante que o mesmo termo não
dispara dois jobs simultâneos.

---

## Interfaces/contratos

Assinaturas públicas (sem corpo). `?` = pode ser `null` (documentar no
Javadoc). Records são imutáveis; listas copiadas com `List.copyOf` no
construtor compacto.

### A. `catalog` (domínio — não importa nada de `ingestion` nem `api`)

`SourceCode` — enum `SIMBAD`, `NASA_EXO` (o `name()` é o `source.code` no banco).

`Epistemic` — enum `OBSERVED, DERIVED_BY_SOURCE, COMPUTED_BY_US, ESTIMATED, MODEL`
com `String dbValue()` (`'observed'`…) e `static Epistemic fromDb(String)`.

**identifier/IdentifierNormalizer** (classe final, métodos estáticos, puro)
- `static String normalize(String raw)` — regras, nesta ordem:
  1. `null`/em branco → `IllegalArgumentException`;
  2. Unicode NFKD e remoção de marcas diacríticas (`Andrômeda` → `andromeda`);
  3. `toLowerCase(Locale.ROOT)` e `trim`;
  4. remove prefixo de token inicial `name `, `* ` e `v* ` (convenções do
     SIMBAD: `NAME Betelgeuse`, `* alf Ori`, `V* alf Ori`);
  5. remove **todo** whitespace.
  Exemplos obrigatórios no teste: `'M  31'`, `'M31'`, `'m 31'`, `' M 31 '` → `'m31'`;
  `'NAME Betelgeuse'` → `'betelgeuse'`; `'* alf Ori'` → `'alfori'`;
  `'NAME Sgr A*'` → `'sgra*'`.
  Não remove zeros à esquerda (`M031` ≠ `M31`) — limitação consciente (R10).
- `static String catalogOf(String identifier)` — primeiro token antes do
  primeiro espaço (`'M  31'`→`'M'`, `'NAME Betelgeuse'`→`'NAME'`,
  `'* alf Ori'`→`'*'`); identificador sem espaço → `null`.

Este normalizador é **o mesmo** usado na gravação (`CatalogWriter`) e na
busca (`SearchService`). Uma função só, chamada nos dois lados — é isso que
faz `M31` achar `M  31`.

**identifier/IdentifierRepository**
- `void upsertAll(UUID objectId, short sourceId, List<IdentifierRow> rows)` —
  `ON CONFLICT (object_id, normalized) DO NOTHING` (dois idents que
  normalizam igual no mesmo objeto: fica o primeiro). As linhas chegam com
  `normalized` e `catalog` já calculados pelo `CatalogWriter`.
- `List<UUID> findObjectIdsBySourceIdentifiers(short sourceId, Collection<String> normalized)` —
  objetos distintos que já têm algum desses identificadores vindos dessa fonte.
- `List<IdentifierMatch> findExact(String normalized, int limit)`
- `List<IdentifierMatch> findFuzzy(String normalized, double minSimilarity, int limit)` —
  `similarity(normalized, :q) >= :min`, ordenado por similaridade desc.
- `List<IdentifierRow> findByObject(UUID objectId)`
- `IdentifierMatch(UUID objectId, String identifier, String normalized, double score)`
- `IdentifierRow(String identifier, String normalized, String catalog?, String lang?, boolean isCommonName, String sourceCode?)`

**category/**
- `TypeMappingRule(int id, String sourceType, String morphPattern, int categoryId, String categoryCode, short priority)`
- `CategoryDecision(int categoryId, String categoryCode, String basis)`
- `CategoryResolver` (puro): `CategoryDecision resolve(String sourceCode, String sourceType, String morphType?, List<TypeMappingRule> rules, CategoryRow fallbackOther)` —
  filtra `sourceType` exato; mantém regras com `morphPattern` vazio ou cujo
  regex dá `find()` no `morphType` (se `morphType` nulo, só as vazias);
  maior `priority` vence; empate → menor `id` (determinístico). Sem regra →
  `fallbackOther`. `basis` sempre preenchido com o raciocínio.
  Regex inválido no banco → regra ignorada e registrada em log WARN (não
  derruba a ingestão). Compilar `Pattern` uma vez por regra (cache por id).
- `CategoryRepository`: `List<TypeMappingRule> findRules(short sourceId)`,
  `Optional<CategoryRow> findByCode(String code)`, `Optional<CategoryRow> findById(int id)`
- `CategoryRow(int id, String code, Integer parentId?, String namePt, String nameEn)`

**source/SourceRepository**: `SourceRow getByCode(SourceCode code)` (lança
`IllegalStateException` se a V2 não rodou), `List<SourceRow> findByIds(Collection<Short> ids)`.
`SourceRow(short id, String code, String name, String homepageUrl, String termsUrl?, String acknowledgment?)`

**object/**
- `SlugFactory` (puro): `String baseSlug(String displayName)` — expande
  catálogo `M` → `messier` (`'M 31'` → `'messier-31'`, como no exemplo do doc),
  remove diacríticos, minúsculas, não-alfanumérico → `-`, colapsa e apara
  hífens, máx. 120 chars. `String withSuffix(String base, int n)` → `base-n`.
  **Slug é definido na criação e nunca muda** (URL estável), mesmo que o
  display name mude depois.
- `DisplayNameRule` (puro): `String choose(String mainId, List<IdentifierWrite> ids)` —
  (1) se há identificador de catálogo `M` → ele com espaços colapsados (`'M 31'`);
  (2) senão, o `NAME …` mais curto (empate: alfabético), sem o prefixo;
  (3) senão `mainId` com espaços colapsados.
- `CelestialObjectRepository`:
  `UUID insert(String slug, String displayName, Integer categoryId?, String categoryBasis?)`,
  `void updateClassification(UUID id, String displayName, Integer categoryId?, String categoryBasis?)` (atualiza `updated_at`),
  `boolean slugExists(String slug)`, `Optional<ObjectRow> findBySlug(String slug)`,
  `List<ObjectRow> findByIds(Collection<UUID> ids)`
- `ObjectRow(UUID id, String slug, String displayName, Integer categoryId?, String categoryBasis?, String existenceStatus, Instant createdAt, Instant updatedAt)`
- `ObjectSourceTypeRepository`: `void upsert(UUID objectId, short sourceId, String sourceType, String morphType?, long runId)`,
  `List<SourceTypeRow> findByObject(UUID objectId)`;
  `SourceTypeRow(String sourceCode, String sourceType, String morphType?)`.
- `AmbiguousLocalMatchException` (unchecked) — identificadores do objeto que
  chega casam com 2+ objetos já gravados.

**position/**
- `PositionWrite(double raDeg, double decDeg, String frame, String epoch, Double posErrMas?, String bibcode?, Instant retrievedAt)`
  (`gal_l/gal_b/constellation` ficam NULL no Bloco 1 — são "calculados por
  nós" e entram depois, marcados como tal.)
- `PositionRepository`:
  `void replaceForSource(UUID objectId, short sourceId, PositionWrite p, Long referenceId?)` —
  `object_position` **não tem** UNIQUE no esquema aprovado; idempotência via
  DELETE (objeto, fonte) + INSERT na mesma transação.
  `void recomputePreferred(UUID objectId)` — marca a linha mais recente
  (com uma fonte só, é a única).
  `Optional<PositionRow> findPreferred(UUID objectId)`
- `PositionRow(double raDeg, double decDeg, String frame, String epoch, String sourceCode, String bibcode?, Instant retrievedAt)`

**reference/BibReferenceRepository**
- `Map<String, Long> upsertBibcodes(Set<String> bibcodes)` — só `bibcode`
  (título/autores/DOI ficam NULL até existir integração com ADS). Truque para
  obter o id mesmo em conflito: `ON CONFLICT (bibcode) DO UPDATE SET bibcode = EXCLUDED.bibcode RETURNING id, bibcode`.

**measurement/**
- `MeasurementKey(String propertyCode, String qualifier, short sourceId, String dataset)` — espelha o UNIQUE da tabela.
- `MeasurementWrite(String propertyCode, String qualifier, Double valueNum?, Double errMinus?, Double errPlus?, String limitFlag?, String valueText?, String originalValue, String originalUnit?, Epistemic epistemic, String method?, List<Long> derivedFrom, String dataset, String sourceUrl, String bibcode?, Long referenceId?, Long rawPayloadId?, Instant retrievedAt)`
  + `MeasurementWrite withReferenceId(Long id)`.
  Contrato: quem monta a medição a partir da fonte preenche `bibcode`
  (texto); o `CatalogWriter` resolve `bibcode → referenceId` via
  `BibReferenceRepository` e passa a cópia com `referenceId` para o
  repositório. O repositório grava **só** `referenceId` (a coluna da tabela).
  `DerivedValueService`, que já tem o id da referência da paralaxe, preenche
  `referenceId` direto.
- `MeasurementRepository`:
  `long upsert(UUID objectId, short sourceId, MeasurementWrite w)` — `INSERT … ON CONFLICT (object_id, property_code, qualifier, source_id, dataset) DO UPDATE SET <todas as colunas de valor/proveniência> RETURNING id`; **não** toca `is_preferred`.
  `int deleteStale(UUID objectId, short sourceId, Set<MeasurementKey> keep)` — apaga medições da fonte para o objeto, **exceto** `computed_by_us` e as chaves em `keep` (valor que sumiu da fonte não fica fantasma no banco).
  `int deleteByKey(UUID objectId, MeasurementKey key)`
  `List<MeasurementRow> findByObject(UUID objectId)`
  `List<MeasurementRow> findByObjectAndProperty(UUID objectId, String propertyCode)`
  `void applyPreferred(UUID objectId, Set<Long> preferredIds)` — primeiro `false` em todas do objeto, depois `true` nas escolhidas, na mesma transação (a ordem importa por causa do índice único parcial `one_preferred`).
- `MeasurementRow` — todas as colunas da tabela + `String sourceCode` + `String bibcode?` (via join).
- `PreferenceCandidate(long id, String propertyCode, String qualifier, short sourceId, Double valueNum?, Double errMinus?, Double errPlus?, Instant retrievedAt)`
- `PriorityRule(String propertyCode, Integer categoryId?, short sourceId, short rank)`; `PriorityRuleRepository.findAll()`.
- `PreferencePolicy` (puro): `Set<Long> choosePreferred(List<PreferenceCandidate> candidates, List<PriorityRule> rules, Integer categoryId?)` — agrupa por `(propertyCode, qualifier)` e escolhe **exatamente um** por grupo, na ordem da seção 7 do doc: (1) menor `rank` aplicável (regra com `categoryId` igual vence regra com `categoryId` NULL; fonte sem regra fica depois das com regra); (2) menor incerteza relativa `max(errMinus, errPlus)/|valueNum|` — quem não tem incerteza perde para quem tem; (3) `retrievedAt` mais recente; (4) menor `id` (só para ser determinístico).
- `PreferenceService`: `void recompute(UUID objectId)` — carrega candidatos, regras e a categoria do objeto, chama a policy, `applyPreferred`, e `PositionRepository.recomputePreferred`.

**derived/**
- `DistanceComputation` — `sealed interface` com dois records:
  `Computed(double valuePc, double errMinusPc, double errPlusPc, double snr)` e
  `NotComputed(Reason reason, Double snr?)`, `enum Reason { NO_UNCERTAINTY, NON_POSITIVE_PARALLAX, SNR_BELOW_THRESHOLD }`.
- `DistanceFromParallax` (puro): `static final double MIN_SNR = 5.0`;
  `static DistanceComputation compute(double parallaxMas, Double parallaxErrMas?)`.
  Regras: erro nulo ou ≤ 0 → `NO_UNCERTAINTY`; paralaxe ≤ 0 → `NON_POSITIVE_PARALLAX`;
  `snr = plx/σ < 5` → `SNR_BELOW_THRESHOLD`; senão `d = 1000/plx` pc e o
  **intervalo assimétrico exato**: `errMinus = d − 1000/(plx+σ)`,
  `errPlus = 1000/(plx−σ) − d` (com S/N ≥ 5, `plx−σ > 0` sempre). Explicação
  didática: inverter a paralaxe distorce a incerteza — a distância é mais
  incerta "para longe" que "para perto"; por isso o doc pede intervalo, e
  não `± σ` simétrico.
- `DerivedValueService`: `void recompute(UUID objectId)` — para **cada**
  medição `parallax` do objeto (uma por fonte): `Computed` → `upsert` de
  `distance` com `qualifier=''`, `dataset='computed:inverse_parallax'`,
  `epistemic=COMPUTED_BY_US`, `method='1/plx'`, `derivedFrom=[id da paralaxe]`,
  `sourceId`/`sourceUrl`/`referenceId`/`rawPayloadId`/`retrievedAt` **herdados
  da paralaxe** (a proveniência do cálculo é o dado de entrada),
  `originalValue` = valor calculado em texto, `originalUnit='pc'`.
  `NotComputed` → `deleteByKey` dessa chave (se a paralaxe piorou num refresh,
  a distância calculada antiga some). Nada é gravado para explicar o "não
  calculado": o motivo é derivado na leitura (ver `AbsenceReason`).

**write/**
- `IdentifierWrite(String identifier, boolean isCommonName)`
- `ObjectWrite(SourceCode source, String sourceMainId, String sourceType, String morphType?, List<IdentifierWrite> identifiers, Optional<PositionWrite> position, List<MeasurementWrite> measurements, long runId)` —
  medições e posição chegam com `bibcode` preenchido e `referenceId` nulo.
- `WriteResult(UUID objectId, String slug, boolean created, int measurementsUpserted, int measurementsDeleted)`
- `CatalogWriter`: `WriteResult write(ObjectWrite w)` — **não** abre
  transação (o chamador abre). Passos:
  1. identidade: normaliza todos os identificadores (`IdentifierNormalizer`,
     incluindo `catalogOf`); `findObjectIdsBySourceIdentifiers` → 0 objetos =
     cria (slug via `SlugFactory`, sufixo em colisão), 1 = atualiza, >1 =
     `AmbiguousLocalMatchException` (registro rejeitado com motivo — conflito
     que precisa de humano);
  2. categoria via `CategoryResolver` com as regras da fonte;
  3. display name via `DisplayNameRule`;
  4. identificadores, source type, bibcodes (`upsertBibcodes`), posição,
     medições (`upsert` com `withReferenceId`), `deleteStale`;
  5. `DerivedValueService.recompute`;
  6. `PreferenceService.recompute`.

**read/**
- `MatchType` — `EXACT`, `FUZZY`.
- `SearchHit(String slug, String displayName, String categoryCode?, String categoryNamePt?, String matchedIdentifier, MatchType matchType, double score)`
- `SearchService`: `List<SearchHit> search(String rawQuery, int limit)` —
  normaliza; exatos primeiro; se não houver exato, fuzzy com
  `minSimilarity = 0.3`; **um hit por objeto** (o identificador de melhor
  score); `limit` máx. 20.
- `AbsenceReason` — enum `NOT_PROVIDED_BY_SOURCE`, `PARALLAX_SNR_BELOW_5`,
  `PARALLAX_WITHOUT_UNCERTAINTY`, `PARALLAX_NON_POSITIVE` — cada um com
  `String messagePt()` ("Não disponível na fonte", "Paralaxe com S/N abaixo
  de 5 — distância não calculada"…).
- `MeasurementView(long id, Double valueNum?, Double errMinus?, Double errPlus?, String limitFlag?, String valueText?, String canonicalUnit?, String originalValue, String originalUnit?, String epistemic, String method?, List<Long> derivedFrom, String sourceCode, String dataset, String sourceUrl, String bibcode?, Instant retrievedAt, boolean preferred)`
- `PropertyView(String code, String labelPt, String displayGroup, String qualifier, boolean available, AbsenceReason absenceReason?, Double absenceSnr?, MeasurementView preferred?, List<MeasurementView> alternatives)` —
  uma `PropertyView` por `(code, qualifier)` existente **e** uma por
  `property_definition` sem nenhuma medição (com `available=false` e motivo).
  Para `distance` ausente: se existe `parallax`, o motivo vem de
  `DistanceFromParallax.compute` reaplicado na leitura (uma regra, um lugar).
- `ObjectDetail(ObjectRow object, CategoryRow category?, List<SourceTypeRow> sourceTypes, List<IdentifierRow> identifiers, Optional<PositionRow> position, List<PropertyView> properties, List<SourceRow> sources)`
- `ObjectQueryService`: `Optional<ObjectDetail> findBySlug(String slug)`, `List<String> slugsOf(Collection<UUID> ids)`.

### B. `ingestion.connector` (contrato + modelo canônico)

Adaptação **em lote** do contrato da seção 5.1 do doc (mesmos papéis —
resolver, buscar, normalizar puro — mas a seção 8 exige lote via `IN(...)`,
então os métodos recebem listas):

```
public interface SourceConnector {
    SourceCode code();
    String connectorVersion();                                 // ex. "simbad-tap/1" → ingestion_run.connector_version
    SchemaCheck checkSchema();                                 // 1 consulta TAP_SCHEMA por run
    Resolution resolve(List<String> userQueries);              // nomes → refs da fonte (1 consulta)
    FetchedBatch fetch(List<ResolvedName> resolved);           // N consultas, 1 RawPayload por consulta
    List<NormalizationOutcome> normalize(FetchedBatch batch);  // PURO: sem rede, sem banco, sem relógio
}
```

- `RawPayload(String dataset, String requestUrl, String queryText, String body, String sha256, Instant retrievedAt)` —
  `body` é o texto **exato** recebido; `sha256` é hex do SHA-256 dos bytes UTF-8 de `body`.
- `SourceObjectRef(SourceCode source, String mainId, long sourceInternalId)` —
  `sourceInternalId` é o `oid` do SIMBAD, usado **só** para juntar tabelas
  dentro do mesmo run; não é persistido como identidade (R6).
- `ResolvedName(List<String> queries, String matchedIdentifier, SourceObjectRef ref)` —
  várias consultas do usuário podem resolver para o mesmo objeto.
- `UnresolvedQuery(String query, String reason)` — ex. `"não encontrado em SIMBAD"`,
  `"ambíguo: 2 objetos SIMBAD"`.
- `Resolution(List<ResolvedName> resolved, List<UnresolvedQuery> unresolved, RawPayload raw)`
- `SchemaCheck(boolean ok, List<String> problems, RawPayload raw)`
- `FetchedBatch(List<ResolvedName> resolved, Map<String, RawPayload> payloadsByDataset)`
- `NormalizationOutcome` — `sealed interface` com
  `Accepted(NormalizedObject object)` e `Rejected(ResolvedName name, String reason)`.
- `NormalizedObject(SourceObjectRef ref, List<String> queries, String sourceType, String morphType?, String sourceObjectUrl, List<NormalizedIdentifier> identifiers, Optional<NormalizedPosition> position, List<NormalizedMeasurement> measurements, List<String> issues)` —
  `issues` = avisos por valor (ex. "flux V descartado: valor não numérico"),
  que não rejeitam o objeto mas vão para o `error_summary` do run.
- `NormalizedIdentifier(String identifier, boolean isCommonName)` —
  `isCommonName` = identificador começa com `"NAME "` (convenção do SIMBAD,
  decidida no conector). O conector **não** calcula `normalized` nem
  `catalog`: isso é do `CatalogWriter` via `IdentifierNormalizer` (uma
  normalização só no sistema, ver R9b).
- `NormalizedPosition(double raDeg, double decDeg, String frame, String epoch, String bibcode?, String payloadDataset)`
- `NormalizedMeasurement(String propertyCode, String qualifier, Double valueNum?, Double errMinus?, Double errPlus?, String limitFlag?, String valueText?, String originalValue, String originalUnit?, Epistemic epistemic, String method?, String bibcode?, String dataset, String payloadDataset)` —
  `dataset` entra na chave única da medição; `payloadDataset` diz de qual
  `RawPayload` do lote ela veio (para preencher `raw_payload_id`).
- Exceções (unchecked): `SourceUnavailableException` (rede/5xx/429/circuit
  aberto — transitório; o retry atua) e `SourceQueryException` (4xx, erro de
  ADQL, resposta não-JSON, truncamento — permanente; **não** fazer retry).

### C. `ingestion.connector.tap` (genérico)

- `TapClient` (interface): `RawPayload query(String dataset, String adql)`.
- `HttpTapClient implements TapClient` — construtor
  `(RestClient restClient, URI syncUrl, int maxRec, RateLimiter rl, CircuitBreaker cb, Retry retry, Clock clock)`.
  POST `application/x-www-form-urlencoded` com `REQUEST=doQuery`, `LANG=ADQL`,
  `FORMAT=json`, `MAXREC=<maxRec>`, `QUERY=<adql>`. Ordem dos decorators:
  **Retry (externo) → CircuitBreaker → RateLimiter (interno) → chamada** —
  cada tentativa de retry passa de novo pelo limitador, então o retry nunca
  fura o limite de 2 req/s. Resposta com status 4xx (exceto 429) ou
  `Content-Type` não JSON (o TAP devolve erro em VOTable/XML) →
  `SourceQueryException` com até 500 chars do corpo; 429/5xx/IO →
  `SourceUnavailableException`. `requestUrl` do `RawPayload` = `syncUrl`;
  `queryText` = ADQL exata.
- `TapColumn(String name, String unit?, String datatype?, String ucd?)`
- `TapRow` — `Optional<String> text(String column)` (string vazia/blank = vazio, sempre com `trim`),
  `Optional<Double> number(String column)`, `Optional<Long> integer(String column)`,
  `Optional<String> rawText(String column)` (valor como apareceu no JSON, para `originalValue`).
- `TapResult(List<TapColumn> columns, List<TapRow> rows)` —
  `Optional<TapColumn> column(String name)`, `int size()`.
- `TapJsonParser` (puro): `static TapResult parse(String json)` —
  `{"metadata":[…],"data":[[…]]}`; nomes de coluna comparados em minúsculas;
  `SourceQueryException` se faltar `metadata`/`data`.
- `AdqlLiterals` (puro): `static String quote(String value)` — envolve em
  aspas simples e **dobra** aspas simples internas; rejeita caractere de
  controle. `static String inList(Collection<String> values)`;
  `static String inListNumeric(Collection<Long> values)`.

### D. `ingestion.connector.simbad`

- `SimbadProperties` (`@ConfigurationProperties("galaxy.simbad")`, `@Validated`, record):
  `URI tapUrl` (default `https://simbad.cds.unistra.fr/simbad/sim-tap/sync`),
  `int requestsPerSecond` (2), `Duration connectTimeout` (10s),
  `Duration readTimeout` (60s), `int maxRec` (100000), `int retryMaxAttempts` (3),
  `Duration retryInitialBackoff` (1s), `String userAgent`
  (`"GalaxyExplorer/0.1 (personal research project)"`).
- `SimbadConfig` (`@Configuration`): beans `RateLimiter simbadRateLimiter`
  (limitForPeriod=`requestsPerSecond`, refresh=1s, timeout=30s), `Retry simbadRetry`
  (só `SourceUnavailableException`, backoff exponencial), `CircuitBreaker
  simbadCircuitBreaker` (janela 10 chamadas, abre com 50% de falha, 60s
  aberto), e `TapClient simbadTapClient` (`HttpTapClient` com `RestClient`
  próprio, `JdkClientHttpRequestFactory` com os timeouts, header `User-Agent`).
  Os três componentes Resilience4j são **singletons**: seed e importação sob
  demanda compartilham o mesmo limitador no processo.
- `SimbadIdentifierCandidates` (puro): `static List<String> of(String userQuery)` —
  variantes **distintas, em ordem estável**: original com trim; espaços
  colapsados; `"NAME " + query`; forma com padding de catálogo (**regra
  determinada na Etapa 4 a partir do dado real**: se o `ident` do SIMBAD
  guarda `M   1`/`M  31`/`M 101`, o número é alinhado à direita em 3 colunas
  após `M `). Motivo: o `ident.id` do SIMBAD tem espaçamento próprio
  (descoberta 3 do doc) e a comparação no ADQL é por string. Isto **não** é
  normalizador de busca: gera grafias *do SIMBAD*.
- `SimbadQueries` (puro, ADQL em **uma linha**, determinística — IN-lists com
  oids em ordem crescente, candidatos na ordem de entrada):
  `static String schemaCheck()` — `TAP_SCHEMA.columns` (table_name, column_name, unit, datatype) para `basic`, `ident`, `flux`, `mesDistance`;
  `static String resolve(Collection<String> candidates)` — `ident` JOIN `basic`: `matched_id` (= `ident.id`), `oid`, `main_id`;
  `static String basic(Collection<Long> oids)` — colunas: `oid, main_id, otype, ra, dec, coo_bibcode, plx_value, plx_err, plx_bibcode, rvz_redshift, rvz_radvel, rvz_type, rvz_err, rvz_bibcode, sp_type, sp_bibcode, morph_type, morph_bibcode, galdim_majaxis, galdim_minaxis, galdim_bibcode`
  (`rvz_err`, `sp_bibcode`, `morph_bibcode`, `galdim_bibcode` vão além da sua
  lista porque "proveniência completa" exige a referência de cada valor —
  **incluir só se o `TAP_SCHEMA` confirmar que existem**; se alguma não
  existir, remover, nunca inventar);
  `static String identifiers(Collection<Long> oids)` — `ident`: `oidref, id`;
  `static String fluxes(Collection<Long> oids)` — `flux`: `oidref, filter, flux, flux_err, bibcode`;
  `static String distances(Collection<Long> oids)` — `mesDistance`: colunas
  **confirmadas no TAP_SCHEMA** (esperado: `oidref, dist, unit, minus_err, plus_err, method, bibcode`).
- `SimbadSchemaExpectations` (puro):
  `static SchemaCheck evaluate(RawPayload tapSchemaPayload)` (todas as colunas
  esperadas existem? unidades batem?) e
  `static List<String> unitProblems(String dataset, TapResult result)` —
  confere o `unit` do `metadata` de **cada resposta** contra o esperado
  (`ra/dec`, `plx_value/plx_err`, `rvz_radvel`, `galdim_*`). As strings
  esperadas (`"deg"`, `"mas"`, `"km.s-1"`, `"arcmin"`…) são **copiadas da
  fixture capturada**, não da memória.
- `SimbadUnits` (puro):
  `static Optional<Double> distanceToParsecs(double value, String unit)` —
  `pc`, `kpc`, `Mpc` (confirmar grafia no dado); unidade desconhecida →
  vazio (vira `issue`, valor descartado — nunca converter no chute).
  `static String canonicalUnitFor(String sourceUnit)` — tradução da grafia
  VOUnit da fonte para a canônica nossa (ex. `km.s-1` → `km/s`); grafia
  desconhecida → `IllegalArgumentException` (é bug de mapeamento, não dado).
- `SimbadValidator` (puro): `static List<String> validateBasic(TapRow row)` —
  rejeição do objeto se faltar `main_id` ou `otype`; faixas físicas que
  descartam **só o valor** (com `issue`): `ra ∈ [0,360)`, `dec ∈ [-90,90]`,
  magnitude ∈ [-30, 40], `redshift > -1`, erros ≥ 0. Paralaxe ≤ 0 é **guardada**
  (é uma medição real), só não gera distância.
- `SimbadMapper` (puro): `static List<NormalizationOutcome> normalize(FetchedBatch batch)` —
  regras:
  - Problema de unidade em qualquer payload do lote → **todos** do lote
    `Rejected` com o motivo (`"UNIT_MISMATCH basic.plx_value: esperado mas, veio arcsec"`).
    Melhor rejeitar do que gravar valor 1000× errado.
  - `parallax`: `plx_value`/`plx_err` (erro simétrico → `errMinus=errPlus`), `epistemic=OBSERVED`, `dataset='basic'`, bibcode `plx_bibcode`.
  - `redshift` e `radial_velocity`: ambos vêm do SIMBAD; o `rvz_type` indica
    qual foi **medido** — esse vai `OBSERVED` (com `rvz_err`), o outro
    `DERIVED_BY_SOURCE`. Confirmar os valores possíveis de `rvz_type` no dado.
  - `spectral_type`, `morph_type`: texto, `OBSERVED`, bibcodes próprios.
  - `ang_size_major/minor`: `galdim_*`, `OBSERVED`, `galdim_bibcode`.
  - `app_mag`: uma por linha de `flux`, `qualifier=filter`, `dataset='flux'`.
  - `distance` de `mesDistance`: `DERIVED_BY_SOURCE` (distância é sempre
    inferida por um método), convertida para pc com `SimbadUnits`
    (`originalValue`/`originalUnit` preservam o que veio), `method` da coluna
    `method`, `qualifier=''`, **`dataset = "mesDistance:" + bibcode + ":" + method`**.
    Motivo: o UNIQUE `(objeto, propriedade, qualificador, fonte, dataset)`
    permite **guardar todas** as medições publicadas (princípio 3 do doc:
    conflito é dado), cada uma com sua referência, e o `is_preferred` escolhe
    uma por política. Linhas duplicadas na mesma chave → mantém a primeira,
    gera `issue`. `payloadDataset='mesDistance'`.
  - `originalValue`: `TapRow.rawText` — o valor exatamente como estava no JSON.
  - `sourceObjectUrl`: `https://simbad.cds.unistra.fr/simbad/sim-id?Ident=<main_id URL-encoded>` —
    é esse link que vai em `measurement.source_url` (link clicável para o
    usuário conferir); URL do TAP + ADQL ficam no `raw_payload`.
  - Identificadores: todas as linhas de `ident` do `oid`, como
    `NormalizedIdentifier(id, id.startsWith("NAME "))`.
  - Posição: `ra`/`dec` em graus, `frame='ICRS'`, `epoch='J2000'`, `coo_bibcode`.
- `SimbadConnector implements SourceConnector` (`@Component`), injeta
  `@Qualifier("simbadTapClient") TapClient` — junta tudo:
  `checkSchema` → `query("tap_schema", SimbadQueries.schemaCheck())` + `evaluate`;
  `resolve` → candidatos de todas as consultas, 1 query, remapeia `matched_id`
  → consulta(s) de origem; consulta que casa com 2+ `oid` diferentes →
  `UnresolvedQuery` "ambíguo";
  `fetch` → 4 queries (`basic`, `ident`, `flux`, `mesDistance`), cada uma
  um `RawPayload`; se alguma tiver `size() == maxRec` → `SourceQueryException("resultado truncado em MAXREC")` (R7);
  `normalize` → delega a `SimbadMapper`.
  `connectorVersion()` = `"simbad-tap/1"` (incrementar sempre que o mapper
  mudar de comportamento).

### E. `ingestion.pipeline`

- `IngestionProperties` (`@ConfigurationProperties("galaxy.ingestion")`, record):
  `int batchSize` (default 10, `@Min(1) @Max(50)`). Fica no pipeline, não no
  SIMBAD, porque o pipeline é genérico e não deve importar nada de um
  conector específico.
- `RunTrigger` — enum `SEED, SCHEDULED, ON_DEMAND, MANUAL` (`dbValue()` minúsculo).
- `RunStatus` — enum `RUNNING, OK, PARTIAL, FAILED`.
- `IngestionRequest(SourceCode source, RunTrigger trigger, List<String> queries)` (não vazia; máx. 500).
- `ObjectOutcome(String query, UUID objectId?, String slug?, boolean created, String failureReason?)`
- `IngestionReport(long runId, RunStatus status, int recordsOk, int recordsFailed, List<ObjectOutcome> outcomes, List<String> issues)`
- `IngestionRunRepository`: `long open(short sourceId, RunTrigger t, String connectorVersion)`,
  `void close(long runId, RunStatus s, int ok, int failed, String errorSummary?)`,
  `int failDangling(String reason)` (runs `running` deixados por crash → `failed`).
- `RawPayloadRepository`: `long insert(long runId, RawPayload p)` — `body`
  gravado com `CAST(:body AS jsonb)`.
- `NormalizedToCatalog` (puro): `static ObjectWrite toWrite(NormalizedObject o, Map<String, Long> payloadIdsByDataset, long runId)` —
  traduz modelo canônico → entrada do catálogo (preenche `rawPayloadId` por
  `payloadDataset`, `sourceUrl = sourceObjectUrl`).
- `IngestionPipeline` (`@Service`): construtor recebe `List<SourceConnector>`
  (indexa por `code()`), `IngestionRunRepository`, `RawPayloadRepository`,
  `SourceRepository`, `CatalogWriter`, `TransactionTemplate`,
  `IngestionProperties`. Método único:
  `IngestionReport run(IngestionRequest request)`.

  Sequência (seção 8 do doc, passo a passo):
  1. `open` do run — **transação própria, commitada já** (se tudo explodir
     depois, o run existe e fica `failed`, não some).
  2. `checkSchema` → grava `RawPayload` → se `!ok`, fecha `failed` com os
     problemas e retorna (nada escrito no catálogo).
  3. Particiona `queries` em lotes de `batchSize`.
  4. Por lote: `resolve` → grava raw → `unresolved` viram `ObjectOutcome`
     com falha; `fetch` → grava cada raw (map dataset → id);
     `normalize` (puro).
  5. Por `Accepted`: **uma transação por objeto** (`TransactionTemplate`):
     `CatalogWriter.write(NormalizedToCatalog.toWrite(...))`. Exceção num
     objeto → rollback só dele, `ObjectOutcome` com falha, o run segue.
     `Rejected` → falha com o motivo.
  6. `SourceUnavailableException` no meio (circuit aberto, retry esgotado) →
     lotes restantes contados como falha com esse motivo; o run fecha.
     `SourceQueryException` num lote → lote inteiro falha com o motivo; os
     próximos lotes seguem.
  7. `close`: `OK` se 0 falhas; `PARTIAL` se ≥1 ok e ≥1 falha; `FAILED` se 0 ok.
     `error_summary` = falhas + issues, truncado em 4000 chars.
  (Passo 7 do doc — derivados e `is_preferred` — acontece dentro do
  `CatalogWriter.write`, por objeto, na mesma transação do upsert.)

### F. `ingestion.jobs`

- `ImportJobStatus` — enum `QUEUED, RUNNING, SUCCEEDED, NOT_FOUND, FAILED`.
- `ImportJob(long id, String query, String normalizedQuery, ImportJobStatus status, String message?, Long runId?, List<UUID> resultObjectIds, Instant createdAt, Instant startedAt?, Instant finishedAt?)`
- `ImportJobRepository`: `ImportJob insertQueued(String query, String normalized)` (propaga `DuplicateKeyException` se já há ativo), `Optional<ImportJob> findActiveByNormalized(String n)`, `Optional<ImportJob> findById(long id)`, `void markRunning(long id)`, `void markFinished(long id, ImportJobStatus s, String message?, Long runId?, List<UUID> ids)`, `int failInterrupted(String message)`.
- `ImportExecutorConfig`: bean `ThreadPoolTaskExecutor importExecutor` —
  core=1, max=1, fila=`galaxy.imports.queue-capacity`. Uma thread só: as
  importações são serializadas (o rate limiter já é o gargalo; paralelizar não
  ganharia nada e complicaria).
- `ImportJobService`: `ImportJob submit(String rawQuery)` — normaliza; se já
  existe job ativo para o termo, devolve ele (idempotência; corrida resolvida
  pelo índice único → captura `DuplicateKeyException` e relê o ativo); senão
  insere e submete ao executor; fila cheia → marca o job `FAILED` e lança
  `ImportQueueFullException`. `Optional<ImportJob> find(long id)`.
  `@EventListener(ApplicationReadyEvent)` → `failInterrupted` +
  `IngestionRunRepository.failDangling` ("interrompido por reinício").
- `ImportJobWorker`: `void execute(long jobId)` — `markRunning`; consulta
  `SearchService` e, se houver hit `EXACT`, termina `SUCCEEDED` com mensagem
  "já existia no catálogo", sem chamar o SIMBAD; senão
  `IngestionPipeline.run(SIMBAD, ON_DEMAND, [query])`: objeto gravado →
  `SUCCEEDED`; não resolvido → `NOT_FOUND` "Objeto não encontrado em SIMBAD";
  outra falha → `FAILED` com o motivo. Qualquer exceção não prevista →
  `FAILED` (o job **nunca** fica preso em `RUNNING`).

### G. `ingestion.seed`

- `MessierDesignations` (puro): `static List<String> all()` → `"M 1"` … `"M 110"`
  (110 itens, gerados por laço — é a lista de *consultas*, não dado astronômico).
- `MessierSeedRunner implements ApplicationRunner`, `@Profile("seed")`:
  `IngestionPipeline.run(SIMBAD, SEED, MessierDesignations.all())`; loga o
  relatório (ok/falhas com motivo, otypes que caíram em `other`); encerra com
  `SpringApplication.exit(context, …)` e `System.exit` — código 0 para `OK`/
  `PARTIAL`, 1 para `FAILED`. Rodar com
  `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=seed"` (aspas
  necessárias no PowerShell). **Decisão:** profile em vez de endpoint admin —
  endpoint admin exigiria autenticação, que não existe ainda; um comando local
  não abre superfície de ataque.

### H. `api`

- `ApiErrorCode` — enum `VALIDATION_FAILED(400)`, `OBJECT_NOT_FOUND(404)`,
  `IMPORT_NOT_FOUND(404)`, `IMPORT_QUEUE_FULL(429)`, `INTERNAL_ERROR(500)`, com `HttpStatus status()`.
- `ApiError(Body error)` com `Body(String code, String message, Object details?)` — `details` sempre presente no JSON (`null` quando vazio).
- `ApiException(ApiErrorCode code, String message, Object details?)` (unchecked).
- `ApiExceptionHandler` (`@RestControllerAdvice`): `ApiException`;
  `MethodArgumentNotValidException`/`ConstraintViolationException`/
  `HandlerMethodValidationException`/`MissingServletRequestParameterException`/
  `HttpMessageNotReadableException` → `VALIDATION_FAILED` com `details` =
  lista `{field, message}`; `ImportQueueFullException` → 429 + header
  `Retry-After: 30`; qualquer outra → `INTERNAL_ERROR` **sem** stack trace no
  corpo (log no servidor).
- `SearchController` — `GET /api/v1/search?q=` → 200 `SearchResponse(String query, String normalizedQuery, List<SearchHitDto> hits)`.
  `q`: obrigatório, trim, 1..100 chars. Lista vazia = 200 com `hits: []`
  (o frontend decide disparar import).
  `SearchHitDto(String slug, String displayName, String categoryCode?, String categoryName?, String matchedIdentifier, String matchType, double score)`.
- `ObjectController` — `GET /api/v1/objects/{slug}` → 200 `ObjectDetailResponse`
  ou 404 `OBJECT_NOT_FOUND`. `slug` fora de `^[a-z0-9-]{1,120}$` → 404 direto
  (não consulta o banco).
  `ObjectDetailResponse` (records aninhados): `slug, displayName,
  category{code, namePt, nameEn, basis}, existenceStatus,
  sourceTypes[{source, sourceType, morphType}],
  identifiers[{identifier, catalog, isCommonName, source}],
  position{raDeg, decDeg, frame, epoch, source, reference, retrievedAt} | null,
  properties[{code, label, group, qualifier, available, absence{code, message} | null,
  preferred: Measurement | null, alternatives: [Measurement]}],
  sources[{code, name, homepageUrl, termsUrl, acknowledgment}]`, onde
  `Measurement = {id, value, errMinus, errPlus, limitFlag, valueText, unit,
  originalValue, originalUnit, epistemic, method, derivedFrom, source, dataset,
  sourceUrl, reference{bibcode, adsUrl} | null, retrievedAt}`.
  `adsUrl = https://ui.adsabs.harvard.edu/abs/<bibcode URL-encoded>/abstract`
  (bibcode tem `&`, ex. `2007A&A...474..653V` — **precisa** de encode).
  `sources` lista só as fontes que aparecem no objeto (o crédito obrigatório
  viaja junto com o dado).
- `ObjectDetailMapper`: `static ObjectDetailResponse from(ObjectDetail d)`.
  (DTO separado do read model de propósito: o JSON é contrato com o
  frontend; o read model pode mudar sem quebrar o cliente.)
- `ImportController` —
  `POST /api/v1/imports` corpo `CreateImportRequest(@NotBlank @Size(max=100) @Pattern(...) String query)` →
  **202 Accepted**, header `Location: /api/v1/imports/{id}`, corpo `ImportJobResponse`;
  job ativo já existente para o mesmo termo → 202 com o job existente.
  Padrão permitido: letras/dígitos Unicode, espaço e `. + - * ' _ ( ) / [ ] :`.
  `GET /api/v1/imports/{id}` → 200 `ImportJobResponse` ou 404 `IMPORT_NOT_FOUND`.
  `ImportJobResponse(long id, String query, String status, String message?, List<String> resultSlugs, Long runId?, Instant createdAt, Instant startedAt?, Instant finishedAt?)`.
- Documentação mínima por endpoint (entrada, saída, status possíveis) em
  Javadoc no controller.

### I. Fixtures (capturadas ao vivo — nunca digitadas)

Pares `<nome>.adql` (consulta exata, uma linha) + `<nome>.json` (resposta
real). Captura no **PowerShell com `curl.exe`** (o `curl` puro do PowerShell é
alias de `Invoke-WebRequest`), a partir da pasta das fixtures:

`curl.exe -s -X POST https://simbad.cds.unistra.fr/simbad/sim-tap/sync --data-urlencode "REQUEST=doQuery" --data-urlencode "LANG=ADQL" --data-urlencode "FORMAT=json" --data-urlencode "MAXREC=100000" --data-urlencode "QUERY@<nome>.adql" -o <nome>.json`

(`QUERY@arquivo` lê a ADQL do arquivo — evita o inferno de aspas do
PowerShell e garante que a fixture foi gerada pela consulta exata que está do
lado.) Uma requisição por vez, com pausa — respeitar o limite do SIMBAD.

| nome | conteúdo | uso |
|---|---|---|
| `otypedef_all` | `SELECT otype, label, description FROM otypedef` | validar V2 (todo otype mapeado existe) |
| `ident_spacing_probe` | ident de `M 1`, `M 31`, `M 101` em várias grafias | descobrir regra de padding (Etapa 4) |
| `messier_types` | otype + morph_type distintos dos 110 Messier | montar V2 e testar cobertura do mapeamento |
| `tap_schema` | saída de `SimbadQueries.schemaCheck()` | `SimbadSchemaExpectationsTest` |
| `sample_resolve` | `SimbadQueries.resolve(...)` para o conjunto de amostra | mapper + pipeline IT |
| `sample_basic`, `sample_ident`, `sample_flux`, `sample_mesdistance` | as 4 consultas de `fetch` para os oids de `sample_resolve` | mapper + pipeline IT |
| `error_bad_adql` | uma ADQL propositalmente inválida (registrar também status HTTP e `Content-Type` no README — use `curl.exe -i` numa captura auxiliar) | `HttpTapClientTest` (erro real do TAP, formato real) |

Conjunto de amostra (cobre os casos-limite): `M 31` (AGN + espiral, redshift,
muitas `mesDistance`), `M 1` (SNR, padding de 1 dígito), `M 42` (região HII),
`M 45` (aglomerado aberto, `main_id` não é `M`, tem paralaxe), `M 87`
(elíptica), `M 13` (globular — provável paralaxe com S/N < 5), `M 57` (PN),
`M 82` (irregular), `Betelgeuse` (`s*r`, paralaxe 6.55 ± 0.83 → S/N ≈ 7,9 ≥ 5,
distância calculada), `Sgr A*` (`X`). **Se nenhum objeto da amostra tiver
S/N < 5 na resposta real**, acrescentar candidatos reais (ex. outros
globulares) até achar um e registrar no README — o teste do caso "não
calcula" tem que usar dado real.

`fixtures/simbad/README.md`: data/hora UTC da captura, endpoint, comando
usado, decisões tiradas do dado (padding, unidades, `rvz_type`), e a
observação de que os valores podem mudar no SIMBAD — os testes validam o
**nosso processamento** da resposta gravada, não o estado atual do SIMBAD.

### J. Testes

Regra do projeto: testes de **mapper/parser** usam só fixtures reais.
Testes de **aritmética/regra pura** (`DistanceFromParallax`, `PreferencePolicy`,
`CategoryResolver` com regras construídas no teste) podem usar números
literais — eles testam a fórmula/regra, não afirmam nada sobre o céu.

Integração: nomes terminados em **`IntegrationTest`** (não `IT`) para o
Surefire rodá-los em `mvnw test`, que é o `test_cmd` do qa-gate. **Proibido**
`@Testcontainers(disabledWithoutDocker = true)`: sem Docker o teste deve
**falhar**, nunca pular em silêncio (senão o gate fica verde sem testar o
banco). Usar a `TestcontainersConfiguration` gerada (`@ServiceConnection`,
imagem `postgres:17`).

Substituição do cliente HTTP nos testes de integração: `@TestBean(name = "simbadTapClient")`
(Spring Framework 6.2+/7) apontando para um método fábrica que devolve
`FixtureTapClient` — sem `allow-bean-definition-overriding`, sem rede.

| teste | verifica |
|---|---|
| `IdentifierNormalizerTest` | os exemplos obrigatórios da seção A; nulo/branco lança |
| `SlugFactoryTest`, `DisplayNameRuleTest` | `M 31`→`messier-31`; colisão → sufixo; prioridade M > NAME > main_id |
| `CategoryResolverTest` | prioridade, regex vs vazio, morph nulo, sem regra → other, regex inválido ignorado |
| `DistanceFromParallaxTest` | S/N exatamente 5 calcula; 4,99 não; erro nulo/0; plx ≤ 0; intervalo assimétrico |
| `PreferencePolicyTest` | rank > incerteza > recência > id; sem incerteza perde; um por (prop, qualifier) |
| `AdqlLiteralsTest` | aspas dobradas (`Barnard's Star`), caractere de controle rejeitado |
| `TapJsonParserTest` | parse de `sample_basic.json`: colunas, unidades, nulos |
| `HttpTapClientTest` | `MockRestServiceServer`: form params corretos; corpo de `error_bad_adql` → `SourceQueryException` sem retry; 503 → retry → `SourceUnavailableException`; sha256 do corpo |
| `SimbadQueriesTest` | saída de cada builder == conteúdo do `.adql` correspondente (whitespace normalizado) — garante que fixture e código não divergiram |
| `SimbadIdentifierCandidatesTest` | variantes e ordem estável; `M 1` gera a forma com padding descoberta |
| `SimbadSchemaExpectationsTest` | `tap_schema.json` real passa; o mesmo payload real com um campo `unit` trocado **em memória** falha com mensagem clara |
| `SimbadMapperTest` | sobre as fixtures: M31 `otype=AGN`, `morph_type=SA(s)b`; Sgr A* `otype=X`; Betelgeuse `s*r` com paralaxe e bibcode; mags com `qualifier` de banda; `mesDistance` convertida para pc preservando original; `rvz_type` decide observed/derived; nenhum valor sem `dataset`/`payloadDataset` |
| `MessierDesignationsTest` | 110 itens, primeiro `M 1`, último `M 110`, sem duplicata |
| `MigrationIntegrationTest` | Flyway aplica todas as migrações; tabelas existem; extensão `pg_trgm` instalada; `gen_random_uuid()` funciona |
| `ReferenceDataIntegrationTest` | SIMBAD e NASA_EXO com homepage e acknowledgment; hierarquia (pai de `galaxy_spiral` é `galaxy`); **todo otype do mapeamento existe em `otypedef_all.json`**; **todo otype de `messier_types.json` tem regra** (ou está numa lista explícita "aceito como other" no próprio teste); M31 (`AGN`, `SA(s)b`) → `galaxy_spiral` usando as regras **do banco** |
| `MeasurementRepositoryIntegrationTest` | upsert 2× = 1 linha; `deleteStale` não apaga `computed_by_us`; `one_preferred` impede dois preferidos |
| `CatalogWriterIntegrationTest` | criar → reescrever mesmo objeto = mesmo id/slug; dois objetos com identificador em comum → `AmbiguousLocalMatchException` |
| `IngestionPipelineIntegrationTest` | `FixtureTapClient` + banco real: amostra ingerida; run `ok`; raw_payloads com sha256; Betelgeuse tem `distance` `computed_by_us` com `derived_from` = id da paralaxe e intervalo correto; o objeto de S/N < 5 **não** tem distância calculada; **rodar duas vezes → mesma contagem de linhas** em `measurement`, `object_identifier`, `object_position`, `celestial_object` (idempotência); consulta inexistente → `partial` com motivo |
| `ImportJobServiceIntegrationTest` | termo já existente → `SUCCEEDED` sem chamar o `TapClient` (contador de chamadas do `FixtureTapClient`); termo duplicado ativo → mesmo job; job `RUNNING` vira `FAILED` na recuperação de "restart" |
| `SearchApiIntegrationTest` | `M31`, `m 31`, `M  31` acham `messier-31`; erro de digitação acha por trigram; `q` vazio → 400 `VALIDATION_FAILED` no formato padrão |
| `ObjectApiIntegrationTest` | detalhe com proveniência por valor, `adsUrl` encodado, `available:false` com motivo, slug inexistente → 404 |
| `ImportApiIntegrationTest` | POST → 202 + `Location`; GET do job; corpo inválido → 400 |

`support/FixtureTapClient implements TapClient`: carrega todos os pares de
`fixtures/simbad/`; `query(dataset, adql)` procura o `.adql` com ADQL igual
(whitespace normalizado) e devolve um `RawPayload` com o `.json`; se não
achar, **falha com a ADQL completa na mensagem** — que é exatamente o texto a
salvar e capturar com curl. Nunca gera resposta. Expõe `int callCount()`.

`support/Fixtures`: `static String read(String name)`, `static RawPayload payload(String dataset, String name)`.

---

## Sequência de implementação (passos pequenos e verificáveis)

Cada etapa termina com o comando de verificação passando. Sugestão de
fronteira de commit entre parênteses (commit só quando o usuário pedir).

0. **Pré-voo.** Docker Desktop rodando (`docker info`). Porta 5432 livre:
   `Get-NetTCPConnection -LocalPort 5432 -ErrorAction SilentlyContinue`. Se
   ocupada (ex. PostgreSQL do MechaFinder), definir `POSTGRES_PORT`/`GALAXY_DB_PORT`
   (ex. 5433) no `.env`/ambiente e avisar o usuário.
1. **Compose.** `docker-compose.yml` + `.env.example`. Verificar:
   `docker compose up -d` e `docker compose ps` mostrando `healthy`. (commit: infra)
2. **Projeto.** Baixar de start.spring.io com `curl.exe -o starter.zip "https://start.spring.io/starter.zip?<parâmetros da seção 3>"`
   na raiz, extrair com `tar -xf starter.zip`, apagar o zip. Conferir no
   `pom.xml`: Boot 4.1.1, `java.version` 17, nenhum JPA. Adicionar
   Resilience4j (3 módulos). Verificar: `cd backend; .\mvnw.cmd -B test` —
   o teste de contexto gerado sobe Postgres via Testcontainers e deve passar
   mesmo sem migrações (Flyway sem scripts só avisa). (commit: esqueleto)
3. **V1 + config.** `application.yml`, `ClockConfig`, `V1__core_schema.sql`,
   `MigrationIntegrationTest`. Verificar: `mvnw test` e `.\mvnw.cmd spring-boot:run`
   sobe contra o compose e `GET /actuator/health` = UP.
4. **Reconhecimento ao vivo do SIMBAD (sem código de produto).** Capturar com
   curl: `otypedef_all`, `ident_spacing_probe`, `tap_schema`. Decidir e
   **anotar no README das fixtures**: grafia do `ident` (padding), colunas e
   unidades reais de `basic`/`flux`/`mesDistance`, valores de `rvz_type`.
   Depois capturar `messier_types` (já com a grafia correta).
5. **V2.** Montar `V2__reference_data.sql` a partir das fixtures (URLs de
   `source` checadas com `curl.exe -sI`). `CategoryResolver` +
   `CategoryRepository` + `CategoryResolverTest` + `ReferenceDataIntegrationTest`.
   Verificar: `mvnw test`. (commit: esquema + referência)
6. **Identificadores.** `IdentifierNormalizer`, `SlugFactory`, `DisplayNameRule`
   + testes. Verificar: `mvnw test`.
7. **Camada TAP.** `TapColumn/TapRow/TapResult/TapJsonParser/AdqlLiterals/TapClient/HttpTapClient`,
   exceções do conector, `SimbadProperties`, `SimbadConfig`; capturar
   `error_bad_adql`; testes. Verificar: `mvnw test`.
8. **Consultas + fixtures de amostra.** `SimbadIdentifierCandidates`,
   `SimbadQueries` + testes. Gerar as ADQL do conjunto de amostra (por um
   teste temporário que imprime a saída dos builders, ou pelo
   `FixtureTapClient` falhando com a consulta), salvar como `.adql`, capturar
   os `.json` com curl, na ordem `sample_resolve` → (oids reais da resposta)
   → as 4 de fetch. `SimbadQueriesTest` verde. (commit: conector parte 1)
9. **Mapper.** Contrato `ingestion.connector` (records), `SimbadUnits`,
   `SimbadValidator`, `SimbadSchemaExpectations`, `SimbadMapper`,
   `SimbadConnector` + testes. Verificar: `mvnw test`.
10. **Domínio de escrita.** Repositórios de `catalog`, `DistanceFromParallax`,
    `PreferencePolicy`, `PreferenceService`, `DerivedValueService`,
    `CatalogWriter` + testes unitários e de integração. Verificar: `mvnw test`.
    (commit: catálogo)
11. **Pipeline.** `IngestionProperties`, `IngestionRunRepository`,
    `RawPayloadRepository`, `NormalizedToCatalog`, `IngestionPipeline`,
    `FixtureTapClient`, `IngestionPipelineIntegrationTest` (incluindo
    idempotência). Verificar: `mvnw test`.
12. **Seed real.** `MessierDesignations`, `MessierSeedRunner`, `application-seed.yml`.
    Rodar contra o SIMBAD real com o compose de pé. Verificar no banco
    (`docker compose exec postgres psql -U galaxy -d galaxy`): contagem de
    `celestial_object`; `ingestion_run` com status e contadores; objetos em
    `other` e seus otypes; M31 `galaxy_spiral` com `category_basis`; rodar o
    seed **de novo** e confirmar que as contagens de `measurement` não mudam.
    Reportar números reais ao usuário, inclusive falhas. Se aparecer otype
    Messier sem regra, criar **nova migração** com as regras faltantes (V2 já
    aplicada não se edita — o Flyway acusa checksum). (commit: seed)
13. **API + jobs.** Migração de `import_job` (próximo número livre — `V3` se
    a etapa 12 não precisou de migração extra, senão `V4`), `ingestion.jobs`,
    `SearchService`, `ObjectQueryService`, `api.*`, testes de API.
    Verificar: `mvnw test`, e manualmente com o app rodando:
    `curl.exe "http://localhost:8080/api/v1/search?q=m%2031"`,
    `curl.exe http://localhost:8080/api/v1/objects/messier-31`, POST de import
    de um objeto fora do Messier (ex. `Vega`) e polling até `SUCCEEDED`.
    (commit: api)
14. **Fechamento.** `mvnw test` completo; entregar para `takumi` (cobertura),
    `hattori` e `yagyu` (novas dependências: Resilience4j; entrada do usuário
    indo para ADQL; endpoint público que dispara chamadas externas).

Regra que vale para todas as etapas: **nunca editar migração já aplicada**;
o número da migração é a ordem de criação, não um nome fixo.

---

## Riscos apontados

**R1 — Grafia do `ident` no SIMBAD (alto, bloqueia o seed).** Se a
comparação `ident.id IN ('M 1')` exigir a grafia exata `M   1`, consultas
"naturais" não resolvem. Mitigação: `SimbadIdentifierCandidates` envia
variantes, e a regra de padding é **descoberta na Etapa 4 com dado real**,
não presumida. Se nenhuma variante funcionar de forma genérica para a busca
sob demanda (ex. `Andromeda` sem `NAME`), registrar como limitação e
discutir com o usuário (o resolvedor Sesame do CDS seria a alternativa — é
decisão de arquitetura nova, não implementar por conta própria).

**R2 — Spring Boot 4 é recente (médio).** Starters modularizados, Jackson 3,
Testcontainers 2.x, pacotes de teste movidos (`@AutoConfigureMockMvc` etc.).
Código "de memória" de exemplos Boot 3 vai quebrar em import. Mitigação:
usar o pom gerado como verdade e resolver imports pelo que está no classpath.
Resilience4j fora do Spring (seção 3) elimina a principal incompatibilidade.

**R3 — Conflito de porta 5432 (médio).** O usuário usa PostgreSQL no
MechaFinder; se houver instância local, o compose falha ou — pior — o backend
conecta no banco errado. Mitigação: porta parametrizada + checagem na Etapa 0.

**R4 — qa-gate depende de Docker (médio, intencional).** `mvnw test` sobe
Testcontainers; com Docker Desktop parado o commit é bloqueado. É o
comportamento correto (gate honesto), mas vai surpreender. Se aparecer
"client version 1.32 is too old" (Docker Engine 29+ com Testcontainers
antigo), a versão gerenciada pelo Boot 4.1.1 já deve ser compatível; senão,
conferir a versão do Testcontainers antes de qualquer gambiarra.

**R5 — Entrada do usuário e consultas externas (alto se ignorado — `yagyu`).**
*(Texto atualizado em 2026-10-06 para refletir o código após o ADENDO.)*
`POST /imports {query}` **não** entra em ADQL: o termo só vai ao Sesame, como
componente de URL percent-encoded; as consultas TAP filtram apenas por oid
numérico (`AdqlLiterals.inListNumeric`). Camadas: validação de tamanho e
charset na API, encoding de URL, oids sempre numéricos. Se algum dia texto
externo precisar ir para ADQL, só via `AdqlLiterals.quote`.
Endpoint público que dispara chamada externa: protegido por bind só em
loopback (`server.address`), fila limitada (429 sem gravar nada), deduplicação
por termo, retenção de jobs/payloads e o rate limiter global — sem
autenticação no Bloco 1, aceitável só enquanto o app roda localmente.

**R6 — Identidade do objeto entre execuções (médio, lacuna do esquema).**
O esquema aprovado não tem coluna para o id do objeto na fonte (`oid`,
`main_id`). O Bloco 1 reconhece o mesmo objeto por **interseção de
identificadores SIMBAD** (`CatalogWriter`, passo 1), com rejeição explícita se
casar com 2+ objetos. Funciona, mas é indireto. **Sugestão para o usuário
decidir (não implementar agora):** numa migração futura, adicionar
`source_ref text` em `object_source_type` com UNIQUE `(source_id, source_ref)`.

**R7 — Truncamento silencioso por MAXREC (médio).** O TAP corta resultados
no limite de linhas; `ident`/`mesDistance` de um lote de 10 galáxias pode ter
milhares de linhas (M31 sozinho tem muitas distâncias publicadas). Mitigação:
`MAXREC` explícito alto + checagem `size() == maxRec` → erro, nunca dado
parcial aceito como completo. Se ocorrer, reduzir `galaxy.ingestion.batch-size`.

**R8 — Mapeamento de categoria incompleto (baixo).** O SIMBAD revisou a
nomenclatura de otypes (ex. `s*r` é do esquema novo); listas antigas da
internet estão desatualizadas. Mitigação: V2 só com otypes presentes no
`otypedef` real, teste de cobertura sobre `messier_types.json`, otype
desconhecido cai em `other` com explicação (nunca falha). Regex de morfologia
cobre a notação RC3/de Vaucouleurs; morfologia fora do padrão cai na regra
genérica (galaxy/agn), o que é conservador.

**R9 — Sobreposição de responsabilidade a vigiar.**
(a) A regra "S/N ≥ 5" existe **só** em `DistanceFromParallax`; tanto o
cálculo na escrita quanto o motivo de ausência na leitura chamam essa mesma
função — não duplicar o limiar em SQL ou no `ObjectQueryService`.
(b) `IdentifierNormalizer` é o único normalizador; o conector **não** cria um
próprio (por isso `normalized`/`catalog` são preenchidos no `CatalogWriter`).
`SimbadIdentifierCandidates` não é normalizador: gera grafias **do SIMBAD**.
(c) `is_preferred` só é escrito por `PreferenceService` — nenhum upsert mexe
nele.
(d) `CatalogWriter` não abre transação; `IngestionPipeline` é o único dono da
fronteira transacional da ingestão.
(e) `bibcode → reference_id` só é resolvido no `CatalogWriter`.

**R10 — Mudança futura do normalizador (baixo).** Se `IdentifierNormalizer`
mudar (ex. passar a remover zeros à esquerda, `M031`→`m31`), a coluna
`normalized` já gravada fica inconsistente com a busca. Quando acontecer:
migração/rotina de re-normalização. Registrado para não ser esquecido.

**R11 — Fixtures envelhecem (baixo, aceito).** O SIMBAD atualiza valores; as
fixtures congelam um momento. Os testes validam o processamento, não o céu.
O README das fixtures registra a data; recapturar é uma ação explícita, nunca
"consertar o JSON à mão".

**R12 — Limite do SIMBAD com dois processos (baixo).** O rate limiter é por
processo. Seed (profile `seed`) e servidor rodando ao mesmo tempo somam até
4 req/s — ainda abaixo de 6. Não rodar dois seeds simultâneos.

**R13 — Versões que o plano não fixa.** Imagem `postgres:17` sem minor/digest
e Resilience4j "última 2.x": aceitável para desenvolvimento; fixar antes de
qualquer deploy.

---

## ADENDO (orquestrador, 2026-10-05) — decisões do usuário após o plano. PREVALECE sobre o texto acima.

**R1 resolvido ao vivo.** `ident.id IN ('M 1','M1')` NÃO casa. SIMBAD usa padding fixo:
`'M   1'` (3 espaços), `'M  45'` (2), `'M 110'` (1). E `main_id` de M45 é `'Cl Melotte   22'`
(main_id não é o nome popular — o display_name deve preferir um identificador `NAME ...` ou Messier quando houver).

**Decisão do usuário: resolver nomes via CDS Sesame.**
- Endpoint (testado ao vivo): `GET https://cds.unistra.fr/cgi-bin/nph-sesame/-oxp/S?<nome url-encoded>`
  → XML com `<oid>`, `<oname>`, `<otype>`, `<jradeg>`, `<jdedeg>`. Resolveu M1, Andromeda, Betelgeuse,
  `Sgr A*`, NGC224, TRAPPIST-1. Nome não reconhecido → sem `<oid>` (capture fixture de um nome inexistente para confirmar o formato).
- Fluxo: Sesame → oid → consultas TAP com `WHERE b.oid IN (...)` / `oidref IN (...)`. Nada de montar a grafia com padding.
- Seed Messier: resolver 'M1'..'M110' pelo Sesame (respeitando rate limit CDS compartilhado, ≤ 2 req/s no total entre Sesame e TAP), depois TAP em lote por oid.
- Sesame vira uma fonte registrada (`source.code = 'CDS_SESAME'`)? NÃO — ele só resolve nome, não produz medição. Registre a URL do Sesame na `raw_payload` do run SIMBAD.

**Decisão do usuário: coluna `source_ref`.**
- `object_source_type.source_ref text NOT NULL` (oid do SIMBAD como texto; no futuro pl_name/spkid etc.) com `UNIQUE (source_id, source_ref)`.
- Identidade entre execuções = `(source_id, source_ref)`. Remover a heurística de casar por identificadores (R6 resolvido).
- Como V1 é cópia da seção 6, coloque isso numa migração própria logo após V1.

**R3 resolvido:** porta 5432 ocupada por PostgreSQL de serviço Windows (PID local). Use **5433** no host por padrão no compose e no application.yml (parametrizável via env).

**Docker:** Docker 29 instalado; confirme que o daemon está de pé antes dos testes Testcontainers.
