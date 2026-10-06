# Galaxy Explorer — Fase 0: Análise, Fontes, Arquitetura e Modelo de Dados

> Status: **aprovado** (2026-10-05). Decisões do usuário:
> MVP = SIMBAD + NASA Exoplanet Archive (Sistema Solar/JPL na fase 2);
> acesso a dados = JdbcClient + Flyway. Versões verificadas em start.spring.io
> nesta data: Spring Boot 4.1.1; alvo Java 17 (JDK instalado localmente; Boot 4
> exige ≥ 17).
>
> Decisões posteriores: nomes resolvidos via CDS Sesame (`M1` não casa direto no SIMBAD, que usa `M   1`); `object_source_type.source_ref` guarda o id do objeto na fonte (oid SIMBAD).
>
> Texto original da proposta (2026-10-05). Nenhuma linha de código de
> produto foi escrita ainda. Tudo marcado como "testado ao vivo" foi
> verificado com uma requisição real nesta data, não só lido em documentação.

---

## 1. Leitura da especificação — o que realmente importa

A especificação tem uma prioridade explícita: **dado real e rastreável vem
antes de tudo**. Isso define três regras de arquitetura que guiam o resto:

1. **Todo valor exibido é uma `measurement` com proveniência**, nunca uma
   coluna solta numa tabela. Não existe "a massa de Júpiter"; existe "a massa
   de Júpiter segundo JPL Horizons, revisão de 2022-01, coletada em X".
2. **Ausência é um estado de primeira classe.** O backend responde
   `"available": false` com motivo, e o frontend mostra "Não disponível na
   fonte". Nunca `0`, nunca `—` sem explicação.
3. **Conflito entre fontes é dado, não erro.** Guardamos todos os valores,
   escolhemos um "preferido" por política documentada e mostramos a
   divergência.

---

## 2. O que a pesquisa ao vivo revelou (e por que muda o projeto)

Estas descobertas vieram de consultas reais, e cada uma afeta o desenho:

| # | Descoberta (testado ao vivo) | Consequência no projeto |
|---|---|---|
| 1 | SIMBAD classifica **M31 como `AGN`** (núcleo ativo), não "galáxia espiral". A morfologia `SA(s)b` está em outra coluna (`morph_type`). | A categoria exibida não pode ser cópia do `otype` da fonte. Precisamos de uma **tabela de mapeamento** (`otype` + morfologia → categoria nossa), editável sem código. |
| 2 | SIMBAD classifica **Sgr A\* como `X`** (fonte de raios-X). | "Buraco negro" é uma interpretação científica, não o tipo de catálogo. Guardamos o tipo da fonte *e* a categoria interpretada, com a origem de cada uma. |
| 3 | Identificadores SIMBAD têm espaçamento próprio: `M  31` (2 espaços), `* alf Ori`, `NAME Betelgeuse`. | Busca precisa de **normalização de nome** (`M31`, `m 31`, `M  31` → mesma chave). |
| 4 | **Redshift de M31 diverge**: SIMBAD `-0.0010002`, NED `-0.000991 ± 0.000003` (refs diferentes). | Exemplo real de conflito. Valida a política de prioridade da seção 7. |
| 5 | Betelgeuse: SIMBAD dá paralaxe `6.55 ± 0.83 mas` (Hipparcos, van Leeuwen 2007); **sem** distância em `mesDistance`. Gaia não mede bem estrelas tão brilhantes. | Distância de estrela brilhante **não** vem do Gaia. Erro relativo de 13% → distância deve ser exibida com intervalo, marcada como **calculada** (1/paralaxe). |
| 6 | Exoplanet Archive: a coluna `pl_refname` **não existe** em `pscomppars` (erro ORA-00904); referências ficam em colunas `*_reflink` por parâmetro. | Confirma a instrução "não presuma que a API funciona": cada conector valida suas colunas contra `TAP_SCHEMA` antes de rodar. |
| 7 | `pscomppars` tem **6.375 planetas confirmados** hoje; é "1 linha por planeta, parâmetros de várias referências, **não necessariamente autoconsistentes**" (palavras da própria documentação). | Cada parâmetro guarda sua própria referência. Para conjuntos coerentes (ex.: elementos orbitais), usar a tabela `ps` com uma solução só. |
| 8 | NED: `prefname` é `Messier 031` (case-sensitive); o resolvedor `srs/ObjectLookup` resolve `M31` corretamente. | Para NED, resolver nome via ObjectLookup, depois consultar TAP pelo nome preferido. |
| 9 | JPL **SBDB** devolve JSON estruturado (elementos com `sigma` e unidade). JPL **Horizons** devolve JSON com um **bloco de texto livre** dentro (`"result": "PHYSICAL DATA: Mass x10^26 (kg) = 5.6834 ..."`), revisado em 2022. | SBDB é fácil e robusto. Horizons exige *parser* de texto frágil → isolado em conector próprio com testes de regressão sobre respostas gravadas. |
| 10 | "Saturno" não existe em SIMBAD/NED (são catálogos de fora do Sistema Solar). | Nomes em português e Sistema Solar vêm de **aliases próprios** com idioma, mapeados para IDs JPL. |

---

## 3. Fontes: o que usar para quê

### 3.1 Matriz fonte × categoria

| Categoria | Fonte primária | Complementar | Acesso |
|---|---|---|---|
| Identificação/nomes de qualquer objeto fora do Sistema Solar | **SIMBAD** | NED (extragaláctico) | TAP/ADQL |
| Estrelas (tipo espectral, magnitudes, paralaxe, velocidade radial) | **SIMBAD** | Gaia DR3 (astrometria) | TAP/ADQL |
| Galáxias, quasares, AGNs, aglomerados de galáxias | **NED** (redshift, tipo) | SIMBAD (dimensões, morfologia) | TAP + ObjectLookup |
| Nebulosas, aglomerados estelares, remanescentes de SN | **SIMBAD** | VizieR (catálogos específicos) | TAP |
| Exoplanetas e estrelas hospedeiras | **NASA Exoplanet Archive** (`pscomppars`, `ps`) | SIMBAD (hospedeira) | TAP/ADQL |
| Planetas, luas, Sol | **JPL Horizons** (dados físicos) | — | REST (JSON + texto) |
| Asteroides, cometas, NEA/PHA, TNOs, interestelares | **JPL SBDB** | JPL SBDB Query API (listas) | REST JSON |
| Catálogos especializados (pulsares ATNF, Wolf-Rayet etc.) | **VizieR** | — | TAP — fase posterior |
| Fotometria/espectros/redshift de galáxias | **SDSS** | — | SkyServer — fase posterior |

### 3.2 Detalhes técnicos por fonte

| Fonte | Endpoint (verificado) | Formato | Limites / termos |
|---|---|---|---|
| SIMBAD | `https://simbad.cds.unistra.fr/simbad/sim-tap/sync` | ADQL → JSON/CSV/VOTable | **Máx. 6 consultas/s por IP**, senão IP bloqueado por 1 min. Agradecimento ao CDS obrigatório. |
| NED | `https://ned.ipac.caltech.edu/tap/sync` + `https://ned.ipac.caltech.edu/srs/ObjectLookup` | ADQL → CSV/JSON | Timeout síncrono 4 min (proxy 5); MAXREC padrão 1M. Texto de agradecimento NED. |
| Exoplanet Archive | `https://exoplanetarchive.ipac.caltech.edu/TAP/sync` | ADQL → CSV/JSON | Tabelas `ps` (todas as soluções) e `pscomppars` (composta). Atualização semanal. |
| JPL SBDB | `https://ssd-api.jpl.nasa.gov/sbdb.api` | JSON | `phys-par=1`, `discovery=1`. HTTP 300 = nome ambíguo (lista). |
| JPL Horizons | `https://ssd.jpl.nasa.gov/api/horizons.api` | JSON com texto | `OBJ_DATA=YES`, `MAKE_EPHEM=NO`. |
| Gaia | `https://gea.esac.esa.int/tap-server/tap` | ADQL | DR3 atual. **DR4 anunciado para 2 dez 2026** — o conector deve ter a release como parâmetro, não fixa no código. |
| CDS hips2fits | `https://alasky.cds.unistra.fr/hips-image-services/hips2fits` | JPG/PNG/FITS | Recortes de surveys (DSS2, PanSTARRS, SDSS, 2MASS…) por coordenada. Crédito: "made use of hips2fits, a service provided by CDS" + crédito do survey. |
| NASA Image Library | `https://images-api.nasa.gov/search` | JSON | Sem chave. Mídia NASA em geral domínio público, **mas contém ilustrações artísticas** — precisa curadoria. |
| ESA/Hubble, ESA/Webb | esahubble.org, esawebb.org | páginas/imagens | **CC BY 4.0**, crédito literal obrigatório (ex.: "ESA/Webb, NASA & CSA, …"). |

Nenhuma dessas fontes exige cartão ou conta paga.

---

## 4. Stack — mantenho a sua sugestão, com justificativa

| Camada | Escolha | Por quê |
|---|---|---|
| Frontend | React + TypeScript + Vite | Sugerido; ecossistema do R3F. |
| Dados no front | TanStack Query | Cache, loading/erro e *polling* de importação sem código manual. |
| 3D | Three.js via React Three Fiber + drei | Sugerido; integra com React. |
| Gráficos | Recharts (ou visx) | Barras log, comparações. Decidido na fase de UI. |
| Backend | Java 21 + Spring Boot (versão estável atual) | Sugerido; você já usa Java/Postgres no MechaFinder → reaproveita aprendizado. |
| Acesso a dados | **Spring `JdbcClient` + Flyway** (não JPA) | O modelo é tabela de medições com proveniência; SQL explícito é mais claro que mapear isso em entidades JPA, e você vê exatamente o que roda. |
| Resiliência | Resilience4j (rate limiter, retry, circuit breaker) | Um limitador por fonte (SIMBAD ≤ 6/s). Se o NED cair, o resto continua. |
| Banco | PostgreSQL 17 + `pg_trgm` | Busca tolerante a erro de digitação nos identificadores. |
| Infra | Docker Compose (postgres, backend, frontend) | Sugerido. Java 17 local basta para desenvolver; imagem Docker usa 21. |

**Alternativa considerada e não adotada (por ora):** ingestão em Python com
`astroquery`/`astropy`, que é o padrão da comunidade astronômica e resolve
unidades automaticamente. Não adotei porque (a) as consultas do MVP são
poucas e simples (HTTP + ADQL devolvendo JSON), (b) um segundo runtime dobra
build, Docker e testes, (c) mantém tudo em uma linguagem para você
acompanhar. **Gatilho para reconsiderar:** quando precisarmos de conversões
de coordenadas/cosmologia complexas ou parsing de FITS/VOTable pesado.

---

## 5. Arquitetura

```
 Fontes externas (SIMBAD, NED, Exoplanet Archive, JPL, imagens)
        │  HTTP, nunca chamadas pelo frontend
        ▼
 ┌─────────────────── backend (um app Spring, módulos separados) ──────────┐
 │  ingestion/                                                              │
 │    connector/<fonte>/   ← 1 pacote por fonte, isolado                   │
 │       Client      consulta (rate limit, retry, circuit breaker)          │
 │       Mapper      raw → modelo canônico (unidades, tipos)                │
 │    pipeline       grava raw_payload → valida → normaliza → upsert        │
 │    scheduler      refresh periódico por fonte                            │
 │    jobs           importação sob demanda (pesquisa sem resultado local)  │
 │  catalog/         domínio: objeto, medição, política de preferência     │
 │  facts/           curiosidades calculadas (regras com fórmula exposta)  │
 │  api/             REST /api/v1/...                                       │
 └──────────────────────────────────────────────────────────────────────────┘
        │
        ▼
   PostgreSQL ── frontend React (busca, detalhe, comparação, explorador 3D)
```

**Por que um app só e não microserviços:** projeto pessoal, um desenvolvedor.
O isolamento que a especificação pede ("uma mudança em uma API não quebra o
sistema") é obtido com **fronteiras de pacote + circuit breaker por fonte +
falha de conector registrada em `ingestion_run`**, não com processos
separados. Se um dia a ingestão precisar escalar sozinha, o pacote
`ingestion/` já está pronto para virar outro serviço.

### 5.1 Contrato de um conector

```java
interface SourceConnector {
    SourceCode code();                                  // SIMBAD, NED, ...
    Optional<ResolvedName> resolve(String userQuery);   // nome → id da fonte
    RawPayload fetch(SourceObjectRef ref);              // resposta crua + URL + consulta
    NormalizedObject normalize(RawPayload raw);         // puro, testável sem rede
}
```

`normalize` é **função pura** sobre a resposta gravada. Consequência
importante: os testes usam respostas reais salvas em disco (fixtures) — sem
dados fictícios e sem depender de rede.

### 5.2 Fluxo de pesquisa

1. Usuário digita `M31` → `GET /api/v1/search?q=M31`.
2. Backend normaliza (`m31`) e busca em `object_identifier` (igualdade + trigram).
3. Achou → resultados. Não achou → `POST /api/v1/imports` com o termo:
   job resolve no SIMBAD (e NED/JPL conforme o caso), ingere, frontend faz
   *polling* do job e mostra "Importando de SIMBAD…". Se nenhuma fonte
   reconhecer o nome: "Objeto não encontrado em SIMBAD, NED, JPL" — explícito.

---

## 6. Modelo PostgreSQL (proposta inicial)

Ideia central: **núcleo fixo pequeno + medições genéricas tipadas por
`property_definition`**. Novo tipo de objeto ou nova propriedade = linha nova
em tabela, não migração de esquema. Exceções com tabela própria: posição e
órbita, porque são **conjuntos coerentes** de valores (misturar a
excentricidade de uma solução orbital com o semieixo de outra produz uma
órbita que não existe).

```sql
-- ── Proveniência ───────────────────────────────────────────────
CREATE TABLE source (
  id            smallserial PRIMARY KEY,
  code          text UNIQUE NOT NULL,        -- 'SIMBAD', 'NED', 'NASA_EXO', 'JPL_SBDB'...
  name          text NOT NULL,
  homepage_url  text NOT NULL,
  terms_url     text,
  acknowledgment text                         -- texto de crédito exigido pela fonte
);

CREATE TABLE ingestion_run (
  id            bigserial PRIMARY KEY,
  source_id     smallint NOT NULL REFERENCES source,
  trigger       text NOT NULL CHECK (trigger IN ('seed','scheduled','on_demand','manual')),
  started_at    timestamptz NOT NULL DEFAULT now(),
  finished_at   timestamptz,
  status        text NOT NULL CHECK (status IN ('running','ok','partial','failed')),
  records_ok    int NOT NULL DEFAULT 0,
  records_failed int NOT NULL DEFAULT 0,
  error_summary text,
  connector_version text NOT NULL
);

-- resposta crua: permite auditar e re-normalizar sem reconsultar a fonte
CREATE TABLE raw_payload (
  id            bigserial PRIMARY KEY,
  run_id        bigint NOT NULL REFERENCES ingestion_run,
  request_url   text NOT NULL,
  query_text    text,                          -- ADQL exata, quando houver
  dataset       text,                          -- 'basic', 'pscomppars', 'sbdb'...
  content_sha256 text NOT NULL,
  body          jsonb NOT NULL,
  retrieved_at  timestamptz NOT NULL
);

CREATE TABLE bib_reference (
  id       bigserial PRIMARY KEY,
  bibcode  text UNIQUE,                        -- ADS bibcode, ex. 2007A&A...474..653V
  doi      text,
  title    text, authors text, year smallint
);

-- ── Classificação extensível ──────────────────────────────────
CREATE TABLE object_category (               -- hierarquia: galaxy > spiral > barred_spiral
  id        serial PRIMARY KEY,
  code      text UNIQUE NOT NULL,
  parent_id int REFERENCES object_category,
  name_pt   text NOT NULL,
  name_en   text NOT NULL
);

CREATE TABLE source_type_mapping (           -- 'AGN' do SIMBAD → categoria nossa
  id             serial PRIMARY KEY,
  source_id      smallint NOT NULL REFERENCES source,
  source_type    text NOT NULL,              -- otype / pretype / orbit_class
  morph_pattern  text NOT NULL DEFAULT '',   -- regex opcional sobre morfologia (SA(s)b → espiral); '' = qualquer
  category_id    int NOT NULL REFERENCES object_category,
  priority       smallint NOT NULL DEFAULT 0, -- regra mais específica vence
  UNIQUE (source_id, source_type, morph_pattern)
);

-- ── Objeto ───────────────────────────────────────────────────
CREATE TABLE celestial_object (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  slug          text UNIQUE NOT NULL,        -- 'messier-31'
  display_name  text NOT NULL,
  category_id   int REFERENCES object_category,
  category_basis text,                        -- explica a decisão: 'SIMBAD otype=AGN + morph SA(s)b'
  parent_id     uuid REFERENCES celestial_object,  -- planeta→estrela, lua→planeta
  existence_status text NOT NULL DEFAULT 'confirmed'
                 CHECK (existence_status IN ('confirmed','candidate','disputed','retracted')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE object_source_type (            -- tipo bruto de cada fonte, sem interpretação
  object_id   uuid REFERENCES celestial_object ON DELETE CASCADE,
  source_id   smallint REFERENCES source,
  source_type text NOT NULL,
  morph_type  text,
  run_id      bigint REFERENCES ingestion_run,
  PRIMARY KEY (object_id, source_id)
);

CREATE TABLE object_identifier (
  id          bigserial PRIMARY KEY,
  object_id   uuid NOT NULL REFERENCES celestial_object ON DELETE CASCADE,
  identifier  text NOT NULL,                 -- como a fonte escreve: 'M  31'
  normalized  text NOT NULL,                 -- chave de busca: 'm31'
  catalog     text,                          -- 'M', 'NGC', 'HD', 'NAME'...
  lang        text,                          -- 'pt' para 'Saturno', 'Andrômeda'
  is_common_name boolean NOT NULL DEFAULT false,
  source_id   smallint REFERENCES source,    -- NULL = alias curado manualmente
  UNIQUE (object_id, normalized)
);
CREATE INDEX ON object_identifier (normalized);
CREATE INDEX ON object_identifier USING gin (normalized gin_trgm_ops);

-- ── Medições genéricas ───────────────────────────────────────
CREATE TABLE property_definition (
  code          text PRIMARY KEY,            -- 'distance','mass','radius','teff','redshift','app_mag'...
  label_pt      text NOT NULL,
  canonical_unit text,                       -- 'pc','kg','km','K', NULL p/ adimensional
  value_kind    text NOT NULL CHECK (value_kind IN ('numeric','text')),
  display_group text NOT NULL                -- 'physical','photometry','kinematics'...
);

CREATE TABLE measurement (
  id            bigserial PRIMARY KEY,
  object_id     uuid NOT NULL REFERENCES celestial_object ON DELETE CASCADE,
  property_code text NOT NULL REFERENCES property_definition,
  qualifier     text NOT NULL DEFAULT '',    -- banda p/ magnitude ('V'), método p/ distância
  value_num     double precision,
  err_minus     double precision,
  err_plus      double precision,
  limit_flag    text CHECK (limit_flag IN ('<','>','~')),
  value_text    text,
  original_value text NOT NULL,              -- exatamente como veio
  original_unit  text,
  epistemic     text NOT NULL CHECK (epistemic IN
                 ('observed','derived_by_source','computed_by_us','estimated','model')),
  method        text,                         -- 'trig. parallax', 'Tully-Fisher', '1/plx'...
  derived_from  bigint[],                     -- ids de measurement usados no cálculo
  source_id     smallint NOT NULL REFERENCES source,
  dataset       text NOT NULL,
  source_url    text NOT NULL,
  reference_id  bigint REFERENCES bib_reference,
  raw_payload_id bigint REFERENCES raw_payload,
  retrieved_at  timestamptz NOT NULL,
  is_preferred  boolean NOT NULL DEFAULT false,
  UNIQUE (object_id, property_code, qualifier, source_id, dataset)
);
CREATE UNIQUE INDEX one_preferred ON measurement (object_id, property_code, qualifier)
  WHERE is_preferred;

CREATE TABLE property_source_priority (      -- a política de conflito, como dado
  property_code text REFERENCES property_definition,
  category_id   int REFERENCES object_category, -- NULL = qualquer categoria
  source_id     smallint REFERENCES source,
  rank          smallint NOT NULL,             -- 1 = preferida
  rationale     text NOT NULL
);

-- ── Conjuntos coerentes ──────────────────────────────────────
CREATE TABLE object_position (
  id          bigserial PRIMARY KEY,
  object_id   uuid NOT NULL REFERENCES celestial_object ON DELETE CASCADE,
  ra_deg      double precision NOT NULL,
  dec_deg     double precision NOT NULL,
  frame       text NOT NULL DEFAULT 'ICRS',
  epoch       text NOT NULL DEFAULT 'J2000',
  pos_err_mas double precision,
  gal_l_deg   double precision, gal_b_deg double precision,  -- calculados por nós
  constellation text,                                        -- calculado (IAU), marcado como tal
  source_id   smallint NOT NULL REFERENCES source,
  reference_id bigint REFERENCES bib_reference,
  retrieved_at timestamptz NOT NULL,
  is_preferred boolean NOT NULL DEFAULT false
);

CREATE TABLE orbit_solution (
  id           bigserial PRIMARY KEY,
  object_id    uuid NOT NULL REFERENCES celestial_object ON DELETE CASCADE,
  central_body_id uuid REFERENCES celestial_object,
  epoch_jd     double precision,
  a_au double precision, e double precision, i_deg double precision,
  om_deg double precision, w_deg double precision, ma_deg double precision,
  period_days  double precision,
  uncertainties jsonb,                       -- sigma por elemento
  solution_id  text,                          -- orbit_id do JPL, pl_refname do Exo Archive
  source_id    smallint NOT NULL REFERENCES source,
  reference_id bigint REFERENCES bib_reference,
  retrieved_at timestamptz NOT NULL,
  is_preferred boolean NOT NULL DEFAULT false
);

-- ── Imagens ──────────────────────────────────────────────────
CREATE TABLE object_image (
  id            bigserial PRIMARY KEY,
  object_id     uuid NOT NULL REFERENCES celestial_object ON DELETE CASCADE,
  kind          text NOT NULL CHECK (kind IN
                 ('observation','survey_cutout','processed_composite','artist_impression','diagram')),
  image_url     text NOT NULL,
  thumb_url     text,
  page_url      text NOT NULL,               -- página original da imagem
  provider      text NOT NULL,               -- 'ESA/Webb', 'CDS hips2fits (DSS2)'
  credit_verbatim text NOT NULL,             -- crédito exatamente como exigido
  mission       text,                         -- 'JWST', 'HST', 'DSS2'...
  wavelength    text,                         -- 'óptico', 'infravermelho NIRCam'...
  license_code  text NOT NULL,               -- 'CC-BY-4.0', 'NASA-PD', 'SURVEY-TERMS'
  license_url   text,
  is_hero       boolean NOT NULL DEFAULT false,
  curated_by    text NOT NULL CHECK (curated_by IN ('automatic','manual')),
  retrieved_at  timestamptz NOT NULL
);
```

Notas de projeto:

- **`epistemic`** implementa "observado / calculado / estimado". "Candidato /
  confirmado" é propriedade do **objeto** (`existence_status`), não do valor —
  um exoplaneta candidato tem raio *observado*.
- **`kind = 'artist_impression'`** é obrigatório para ilustrações (comuns em
  exoplanetas). A UI exibe selo "Ilustração artística — não é fotografia".
  Imagem gerada por IA não entra no sistema.
- **Curiosidades não são armazenadas.** São regras no pacote `facts/` que
  rodam sobre medições existentes e devolvem texto + fórmula + ids das
  medições usadas. Sem dado de entrada, a regra simplesmente não dispara.
- **Unidades:** uma unidade canônica por propriedade (distância em pc, massa em
  kg, raio em km, temperatura em K); conversões de exibição (M☉, M⊕, anos-luz)
  usam as constantes nominais IAU 2015 (Resolução B3), citadas na doc.

---

## 7. Política de conflito entre fontes

Ordem de decisão para marcar `is_preferred`:

1. `property_source_priority` para (propriedade, categoria). Exemplos iniciais:
   - redshift de galáxia: **NED > SIMBAD** (NED é especializado em extragaláctico);
   - paralaxe de estrela: **Gaia > SIMBAD**, *exceto* quando o Gaia não tiver
     solução de 5/6 parâmetros (estrelas muito brilhantes, como Betelgeuse);
   - parâmetros de exoplaneta: **Exoplanet Archive**;
   - Sistema Solar: **JPL**.
2. Empate → menor incerteza relativa.
3. Empate → coleta mais recente.

**Exibição do conflito:** se dois valores diferem mais que 3σ combinados (ou
>10% quando não há incerteza), a UI mostra selo "Fontes divergem" e lista
todos. Exemplo real já identificado: redshift de M31 (SIMBAD vs NED).

---

## 8. Ingestão: obter, normalizar, atualizar

**Pipeline por objeto/lote:**

1. `ingestion_run` aberto (fonte, gatilho, versão do conector).
2. Cliente consulta a fonte respeitando o *rate limiter* (SIMBAD: 2 req/s,
   bem abaixo do limite de 6; lotes via `WHERE id IN (...)`).
3. Resposta gravada em `raw_payload` (com ADQL e URL exatas).
4. **Validação:** colunas esperadas existem (checadas contra `TAP_SCHEMA` no
   início do run), tipos numéricos, faixas físicas plausíveis (ex.: `e < 1`
   para órbita fechada, paralaxe > 0 para converter distância). Falha →
   registro rejeitado com motivo, **o run segue** (`status = partial`).
5. **Normalização:** unidade original → canônica (guardando original),
   identificadores normalizados, tipo da fonte → categoria via mapeamento.
6. **Upsert idempotente** na chave `(objeto, propriedade, qualificador,
   fonte, dataset)` — rodar duas vezes não duplica.
7. Recalcula `is_preferred` e valores `computed_by_us` derivados
   (ex.: distância = 1/paralaxe só quando `plx/σ ≥ 5`; abaixo disso, não
   calcula e explica por quê).
8. Fecha o run com contadores.

**Atualização:**

| Fonte | Frequência proposta | Motivo |
|---|---|---|
| Exoplanet Archive | semanal | arquivo atualizado semanalmente |
| JPL SBDB | mensal (e sob demanda) | órbitas refinadas com novas observações |
| SIMBAD / NED | mensal, só objetos já carregados | catálogos estáveis |
| Gaia | por release (DR3 → DR4 em dez/2026) | dados mudam por release, não continuamente |

---

## 9. Proposta de MVP (escopo fechado)

Objetivo: cobrir 7 das 8 buscas de exemplo da especificação com dados reais.

| Item | MVP |
|---|---|
| Fontes | **SIMBAD** (genérica: estrelas, galáxias, nebulosas, aglomerados, Sgr A\*) + **NASA Exoplanet Archive** (TRAPPIST-1 e sistema) |
| Carga inicial | 110 objetos Messier + ~50 estrelas brilhantes nomeadas + sistemas planetários escolhidos — via ingestão real, sem dados digitados |
| Busca | local (exata + trigram) + importação sob demanda via SIMBAD |
| Imagens | recorte automático **hips2fits** (DSS2 colorido) para qualquer objeto com coordenada + imagens heroicas curadas **ESA/Hubble e ESA/Webb (CC BY 4.0)** para alguns objetos Messier |
| Página de detalhe | cards de propriedades com fonte por valor, selos de status, "não disponível", referências (bibcodes → link ADS) |
| Filtros | categoria, faixa de magnitude, faixa de distância |
| Curiosidades | 3 regras: tempo de viagem da luz (distância), percentil de distância no catálogo carregado, escala relativa à Via Láctea (diâmetro) |
| Visualização | esfera celeste 3D (RA/Dec reais, direção correta, **distância não em escala** — avisado na tela) |
| Fora do MVP | Saturno/Sistema Solar (JPL — fase 2), NED, Gaia, VizieR, SDSS, comparação com gráficos (fase 2), mapa 3D com distâncias |

"Saturno" é o único exemplo fora do MVP; entra na fase 2 com JPL.

### Fases seguintes (resumo)

- **Fase 2:** JPL Horizons + SBDB (Sistema Solar, asteroides, cometas); comparação entre objetos com gráficos; aliases em português.
- **Fase 3:** NED (galáxias, redshift preferido) + política de conflito ativa; Gaia DR3/DR4.
- **Fase 4:** explorador 3D com distâncias (escala log, avisada), agrupamentos; VizieR/SDSS.

---

## 10. Processo de trabalho (convenções do seu ambiente)

- Projeto em `C:\Projetos\pessoal\galaxy-explorer`, repositório git próprio.
- `qa-gate` instalado no início (teste backend `mvnw test`, frontend `npm test`, lint).
- Pipeline de agentes: `musashi` (estrutura) → `sasaki` (implementação) →
  `takumi` (testes) → `hattori`/`yagyu` (revisão) antes de cada commit.
- Testes de conectores usam **respostas reais gravadas** das fontes, nunca
  valores inventados.

---

## Fontes consultadas

- NED TAP: https://ned.ipac.caltech.edu/Documents/Guides/Interface/TAP
- SIMBAD TAP: https://simbad.cds.unistra.fr/simbad/sim-tap
- Limite SIMBAD (6 consultas/s): https://cds.unistra.fr/news/2015/08/24-limitation-burst-queries
- NASA Exoplanet Archive TAP: https://exoplanetarchive.ipac.caltech.edu/docs/TAP/usingTAP.html
- JPL SBDB API: https://ssd-api.jpl.nasa.gov/doc/sbdb.html
- JPL Horizons API: https://ssd-api.jpl.nasa.gov/doc/horizons.html
- Gaia Archive / astroquery.gaia: https://astroquery.readthedocs.io/en/stable/gaia/gaia.html
- Gaia DR4 (2 dez 2026): https://www.cosmos.esa.int/web/gaia/news-2026
- CDS hips2fits: https://alasky.cds.unistra.fr/hips-image-services/hips2fits
- NASA Image Library API: https://images.nasa.gov/docs/images.nasa.gov_api_docs.pdf
- ESA/Hubble uso de imagens: https://esahubble.org/copyright/
- ESA/Webb uso de imagens: https://esawebb.org/copyright
