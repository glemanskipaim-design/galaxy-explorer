---
agent: musashi
timestamp: 2026-10-07   # data do ambiente; hora UTC nao obtida (musashi nao tem Bash)
status: COMPLETO (secoes 0–11)
target_files:
  - backend/src/main/resources/db/migration/V8__object_images.sql (novo)
  - backend/src/main/java/dev/galaxyexplorer/catalog/SourceCode.java (+3 valores)
  - backend/src/main/java/dev/galaxyexplorer/catalog/image/** (novo)
  - backend/src/main/java/dev/galaxyexplorer/catalog/position/AngularSeparation.java (novo)
  - backend/src/main/java/dev/galaxyexplorer/catalog/read/** (ObjectDetail, ObjectQueryService, ImageView, ImageViews, ImageAbsenceReason, SearchHit, SearchService, CategoryPage, CategoryQueryService)
  - backend/src/main/java/dev/galaxyexplorer/media/** (novo)
  - backend/src/main/java/dev/galaxyexplorer/ingestion/connector/RawPayload.java (+ fabrica ofBinary)
  - backend/src/main/java/dev/galaxyexplorer/ingestion/connector/image/** (novo)
  - backend/src/main/java/dev/galaxyexplorer/ingestion/connector/hips2fits/** (novo)
  - backend/src/main/java/dev/galaxyexplorer/ingestion/connector/esa/** (novo)
  - backend/src/main/java/dev/galaxyexplorer/ingestion/images/** (novo)
  - backend/src/main/java/dev/galaxyexplorer/ingestion/pipeline/RawPayloadRepository.java (retencao)
  - backend/src/main/java/dev/galaxyexplorer/ingestion/jobs/ImportJobWorker.java (+ recorte apos import)
  - backend/src/main/java/dev/galaxyexplorer/api/image/** (novo), api/object/*, api/search/*, api/category/*, api/error/ApiErrorCode.java (+IMAGE_NOT_FOUND)
  - backend/src/main/resources/application.yml, application-images.yml (novo), images/curation.csv (novo)
  - backend/src/test/** e backend/src/test/resources/fixtures/{hips2fits,esa}/** (capturadas ao vivo)
  - frontend/index.html (CSP img-src), frontend/scripts/capture-fixtures.mjs
  - frontend/src/api/types.ts, frontend/src/domain/{imageKind,imageAlt,imageSrc}.ts
  - frontend/src/components/ui/ProgressiveImage.tsx, frontend/src/features/image/** (novo)
  - frontend/src/features/object/ImagePlaceholder.tsx (removido), features/search/SearchResultItem.tsx, features/category/ObjectList.tsx, pages/ObjectPage.tsx
  - frontend/src/test/fixtures/** (recapturadas)
  - .gitignore (+ backend/data/)
---

# Bloco 3 — Imagens (hips2fits + ESA/Hubble + ESA/Webb)

> **sasaki: leia ESTE arquivo, nao o resumo da conversa.**

Fontes de verdade: `docs/00-fase0-arquitetura.md` (§3.2, §6 `object_image`,
§9 MVP de imagens), planos `bloco1-backend-simbad.md` e
`bloco2-frontend-musashi.md`, codigo atual (commits 4ee0db7 e a448d23) e os
fatos verificados ao vivo pelo orquestrador em 2026-10-07 (hips2fits, HiPS
`properties`, AVM JSON da ESA, busca HTML da ESA). Onde preenchi lacuna:
**[DECISAO NOVA]**, com motivo, para o usuario aprovar ou vetar.

Regras inegociaveis herdadas (nao reabrir):
- nunca imagem gerada por IA; ilustracao artistica sempre marcada como tal e nunca hero;
- crédito/licenca/fonte **vindos da fonte** (AVM, HiPS `properties`), nunca digitados;
- "Imagem não disponível" explicito quando nao houver;
- frontend nao consulta fonte externa (spec §12);
- fixtures reais; nunca metadado de imagem inventado;
- migracoes V1–V7 intocadas; novas a partir de **V8**.

---

## Estrutura proposta

### 0. Visao geral (didatica)

```
            ┌─────────── backend ───────────────────────────────────────────┐
 CDS hips2fits ◄─ connector/hips2fits ─┐                                     │
 CDS HiPS properties ◄─────────────────┤                                     │
 esahubble.org / esawebb.org ◄─ connector/esa ─┤                             │
 cdn.esa*.org (JPG "screen") ◄─ connector/image (download seguro) ─┤         │
                                       ▼                                     │
                         ingestion/images (orquestra: run, raw_payload,      │
                           selecao, hero) ──► media (arquivos em disco)      │
                                       │                                     │
                                       ▼                                     │
                         catalog/image (object_image, regras puras)          │
                                       │                                     │
             api/object, api/search, api/category (DTO de imagem)            │
             api/image  GET /api/v1/images/files/{sha256}.jpg  (bytes)       │
            └────────────────────────────────────────────────────────────────┘
                                       │ mesma origem (/api via proxy do Vite)
                                       ▼
                         frontend: <img src="/api/v1/images/files/…">
```

Direcao de dependencia (a regra mais importante):

```
api ──► catalog.read ──► catalog.image
api.image ──► catalog.image + media
ingestion.images ──► ingestion.connector.{hips2fits,esa,image} ──► (nada de catalog, exceto vocabulario)
ingestion.images ──► catalog.image + catalog.* (leitura de alvos) + media + ingestion.pipeline (runs/payloads)
ingestion.jobs ──► ingestion.images (recorte apos import)
media ──► (nada do dominio)
```

- `catalog.image` e dominio: linhas de `object_image`, licencas, politica de
  hero. Nao sabe o que e AVM, HiPS ou HTTP.
- `ingestion.connector.hips2fits` e `ingestion.connector.esa` sao os unicos
  que conhecem formato de cada fonte (URL do hips2fits, `properties`, AVM,
  HTML de busca). Mudou a ESA? Muda so ali.
- `ingestion.connector.image` e o modelo canonico de imagem + o **download
  seguro** (allowlist de host, limite de tamanho, checagem de JPEG) — um lugar
  so para a regra de seguranca de rede.
- `media` e infraestrutura de arquivo (gravar/abrir por sha256, miniatura).
  Usado pela ingestao (escreve) e pela API (le). Nao conhece objeto celeste.
- **Nao** se usa o contrato `SourceConnector` (resolve/fetch/normalize de
  objeto): imagem nao cria objeto, ela se pendura num objeto que ja existe.
  Forcar o contrato misturaria duas responsabilidades. O que se reaproveita e
  a infraestrutura: `ingestion_run`, `raw_payload`, `ResilientCall`,
  `HttpResponses`, `Clock`, `TransactionTemplate`.

### 1. [DECISAO NOVA] Hospedar vs hot-link — decisao: **HOSPEDAR**

Os JPGs sao baixados **uma vez** na ingestao, gravados em disco
(enderecados por SHA-256) e servidos pelo backend em
`/api/v1/images/files/{sha256}.jpg`. O banco guarda a URL de origem como
proveniencia; o navegador nunca recebe URL de terceiro como `src`.

| Criterio | Hot-link (CDN ESA / URL hips2fits) | Hospedar (escolhido) |
|---|---|---|
| Spec §12 ("fontes externas nao consultadas pelo frontend") | **Viola.** `<img src=hips2fits…>` faz o navegador disparar um *processamento* no CDS a cada visualizacao — e literalmente o frontend consultando a fonte. CDN da ESA e menos grave (arquivo estatico), mas ainda e o navegador do usuario falando com terceiro. | Cumpre: o navegador so fala com a nossa origem. |
| Privacidade (yagyu) | Cada visualizacao expoe IP, User-Agent e horario do usuario a CDS/ESA (Referer da pra cortar; IP nao). | Nenhum terceiro ve o usuario. |
| Desempenho | hips2fits gera na hora (verificado: rapido para M31/M51, mas depende de carga do CDS; FOV grande e lento). | Arquivo local, `Cache-Control: immutable` (URL contem o sha). |
| Disponibilidade | CDS fora = pagina sem imagem. | Independe da fonte depois da ingestao. |
| Rastreabilidade | O que o CDS devolver amanha pode diferir do que registramos hoje (HiPS reprocessado). | Os bytes exibidos sao exatamente os que tem sha256 em `object_image` e `raw_payload`. |
| Licenca | — | **CC BY 4.0** permite copiar/redistribuir com credito. **ODbL-1.0** (HiPS): o recorte e "Produced Work"; exige aviso de que o conteudo vem da base e que ela e ODbL — atendido pelo credito + licenca exibidos. **Atencao DSS**: o `obs_copyright_url` do DSS aponta termos proprios do STScI (placas POSS/UK Schmidt) — ver risco R4. |
| Disco | — | Estimativa: ~110 recortes × (≈40–70 kB + miniatura ≈15 kB) ≈ 10 MB; ESA "screen" ≈ 150–500 kB × talvez 40–60 objetos × ate 3 imagens ≈ 20–60 MB. Total < 100 MB. Irrelevante. |

Consequencia de seguranca reforcada no frontend (enforcement de navegador,
nao convencao): CSP `img-src 'self' data: blob:` no `index.html` — se algum
componente tentar exibir imagem de host externo, o navegador bloqueia (secao
7.4). **[DECISAO NOVA]**

### 2. Tipos de imagem (`kind`, CHECK ja existente na V1)

| Origem | `kind` | Selo na UI | Pode ser hero? |
|---|---|---|---|
| hips2fits (DSS2, PanSTARRS) | `survey_cutout` | "Recorte de survey" | sim (fallback) |
| ESA/Hubble ou ESA/Webb com AVM `Type = Observation` | `processed_composite` | "Imagem processada" | sim (preferida) |
| AVM `Type = Artwork` (so via curadoria manual) | `artist_impression` | "Ilustração artística — não é fotografia" | **nunca** (CHECK no banco) |
| AVM `Type = Chart` (so via curadoria manual) | `diagram` | "Diagrama" | **nunca** (CHECK no banco) |
| AVM `Type` = Simulation, Photographic, Collage, ausente, outro | — | rejeitado, motivo no `error_summary` | — |

`observation` (valor do CHECK) fica reservado para imagem "crua" de missao
(futuro); imagens de divulgacao da ESA sao composicoes coloridas processadas
— chamar de `observation` seria impreciso. **[DECISAO NOVA]**

Selecao automatica da ESA aceita **so** `Observation`. Curadoria manual pode
incluir `Artwork`/`Chart` (sempre com selo, nunca hero).

### 3. Migracao **V8__object_images.sql** (nova; V1–V7 intocadas)

`object_image` existe desde a V1 e esta **vazia** (nada grava nela hoje). A V8
a estende com `ALTER TABLE` (nao recria). Para `ADD COLUMN … NOT NULL` sem
default funcionar, a migracao comeca com uma guarda: `DO $$ … IF EXISTS
(SELECT 1 FROM object_image) THEN RAISE EXCEPTION … $$` — se algum dia houver
linha, a migracao falha alto em vez de inventar valor.

**3.1 Fontes novas em `source`** (URLs verificadas com `curl.exe -sI` no passo
0; acknowledgment copiado literalmente — nunca parafraseado):

| code | name | homepage_url | terms_url | acknowledgment |
|---|---|---|---|---|
| `CDS_HIPS2FITS` | CDS hips2fits | https://alasky.cds.unistra.fr/hips-image-services/hips2fits | https://cds.unistra.fr/legals/ (mesmo do SIMBAD, verificar) | `This research made use of hips2fits, a service provided by CDS.` (verificado ao vivo pelo orquestrador) |
| `ESA_HUBBLE` | ESA/Hubble | https://esahubble.org/ | https://esahubble.org/copyright/ | NULL — credito e por imagem (AVM `Credit`) |
| `ESA_WEBB` | ESA/Webb | https://esawebb.org/ | https://esawebb.org/copyright/ | NULL — idem |

`SourceCode` ganha `CDS_HIPS2FITS`, `ESA_HUBBLE`, `ESA_WEBB` (o `name()` e o
`source.code`; `IngestionPipeline` usa `EnumMap` — valor novo sem conector e
inofensivo).

**3.2 Tabela de referencia `image_license`** (licenca como dado, como
`source_type_mapping`):

| coluna | tipo |
|---|---|
| code | text PK — `CC-BY-4.0`, `ODbL-1.0` |
| name | text NOT NULL — nome oficial (copiado da pagina da licenca) |
| url | text NOT NULL — URL canonica https (verificada ao vivo: https://creativecommons.org/licenses/by/4.0/ e https://opendatacommons.org/licenses/odbl/1-0/) |

Licenca desconhecida = imagem rejeitada (nunca presumir).

**3.3 `ALTER TABLE object_image ADD COLUMN`** (semantica das colunas da V1
documentada em `COMMENT ON COLUMN`):

| coluna | tipo / regra | significado |
|---|---|---|
| source_id | smallint NOT NULL REFERENCES source | `CDS_HIPS2FITS` / `ESA_HUBBLE` / `ESA_WEBB` |
| source_image_id | text NOT NULL | id na fonte: `heic0506a`; para recorte, o id do HiPS (`CDS/P/DSS2/color`) |
| run_id | bigint NOT NULL REFERENCES ingestion_run | |
| metadata_payload_id | bigint REFERENCES raw_payload | AVM JSON ou HiPS `properties` de onde vieram credito/licenca |
| download_payload_id | bigint NOT NULL REFERENCES raw_payload | registro do download dos bytes (ver 3.5) |
| title | text | AVM `Title` / HiPS `obs_title` |
| instrument | text | AVM `Instrument` unido por ", " |
| rights_text | text NOT NULL | AVM `Rights` / HiPS `hips_license`, literal |
| terms_url | text | HiPS `obs_copyright_url` (literal, pode ser `http:`) / pagina de copyright da ESA |
| release_date | date | AVM `Date` (data de **publicacao**, nao de observacao — rotulo na UI diz isso) |
| center_ra_deg, center_dec_deg | double precision | centro da imagem (AVM `Spatial.ReferenceValue` ou centro pedido ao hips2fits) |
| fov_deg | double precision | campo (recorte: pedido; ESA: calculado de `Spatial.Scale` × dimensao, se houver) |
| fov_basis | text | ex.: `"1,25 × tamanho angular (SIMBAD ang_size_major = 13,8′)"` ou `"padrão 0,25°: objeto sem tamanho angular no catálogo"` |
| derived_from_measurement_id | bigint REFERENCES measurement ON DELETE SET NULL | medicao de tamanho angular usada no FOV |
| match_basis | text NOT NULL | por que esta imagem e deste objeto (ex.: `"AVM Subject.Name 'Messier 51' = identificador 'M 51'; centro a 0,4′ do objeto, dentro do quadro"`; recorte: `"recorte centrado na posição preferida (SIMBAD)"`) |
| storage_key | text NOT NULL | caminho relativo do arquivo de exibicao (`ab/<sha>.jpg`) |
| content_sha256 | text NOT NULL | sha256 dos bytes exibidos (= bytes recebidos) |
| width_px, height_px | int NOT NULL | dimensoes reais (lidas do JPEG) |
| byte_size | int NOT NULL | |
| thumb_storage_key | text NOT NULL | miniatura gerada localmente |
| thumb_sha256 | text NOT NULL | |
| thumb_width_px, thumb_height_px | int NOT NULL | |
| gallery_rank | smallint NOT NULL DEFAULT 0 | ordem na galeria (0 = primeiro) |
| updated_at | timestamptz NOT NULL DEFAULT now() | |

Colunas da V1, semantica fixada pela V8:
- `image_url` = URL **exata de onde os bytes foram baixados** (proveniencia), nao a URL servida.
- `thumb_url` = NULL (miniatura e derivada localmente; documentado).
- `page_url` = ESA: AVM `ReferenceURL`; recorte: a propria URL do hips2fits (refaz o mesmo recorte; nao existe "pagina" do recorte).
- `provider` = `"ESA/Hubble"`, `"ESA/Webb"`, `"CDS hips2fits (DSS colored)"` (survey de `obs_title`).
- `credit_verbatim` = AVM `Credit` (texto, ver 6.4) / HiPS `obs_copyright`, literal.
- `mission` = AVM `Facility` unido por ", " / HiPS `obs_title`.
- `wavelength` = AVM `Spectral.Band` se existir na fixture real; senao NULL (nao inventar "óptico").
- `license_code` REFERENCES `image_license(code)` (FK adicionada na V8).
- `license_url` = `image_license.url` (copiado na gravacao).

Restricoes novas:
- `UNIQUE (object_id, source_id, source_image_id)` — idempotencia do upsert.
- `CREATE UNIQUE INDEX one_hero_image ON object_image (object_id) WHERE is_hero;` (espelha `one_preferred`).
- `CHECK (NOT is_hero OR kind IN ('observation','survey_cutout','processed_composite'))` — **ilustracao nunca e hero, garantido pelo banco**.
- `CHECK (content_sha256 ~ '^[0-9a-f]{64}$')` e idem para `thumb_sha256`.
- indice `object_image (content_sha256)` e `(thumb_sha256)` — o endpoint de arquivo procura por sha.

**3.4 Tabela `hips_survey_snapshot`** (proveniencia do credito dos recortes):

| coluna | tipo |
|---|---|
| id | bigserial PK |
| hips_id | text NOT NULL (`CDS/P/DSS2/color`) |
| properties_url | text NOT NULL |
| obs_title, obs_copyright, obs_copyright_url, hips_license, bib_reference | text (literais do `properties`) |
| raw_payload_id | bigint NOT NULL REFERENCES raw_payload |
| retrieved_at | timestamptz NOT NULL |

Um snapshot por run (o `properties` e buscado uma vez por survey por run).
`object_image.metadata_payload_id` do recorte aponta para o mesmo payload.
Motivo de existir alem do payload: consulta simples "qual credito o DSS2
exigia quando gravamos este recorte" sem re-parsear texto.

**3.5 [DECISAO NOVA] Bytes de imagem e `raw_payload`.** `raw_payload.body` e
`jsonb NOT NULL` — guardar JPEG ali e errado (tamanho, tipo). Decisao: o
**arquivo em disco e o artefato cru** (enderecado por sha256); o
`raw_payload` do download guarda `body` = JSON
`{"contentType","byteLength","storageKey"}`, `request_url` = URL exata,
`dataset` = `hips2fits_cutout` / `esa_screen_jpg`, e `content_sha256` = sha256
**dos bytes da imagem** (o mesmo de `object_image.content_sha256`). Nova
fabrica `RawPayload.ofBinary(dataset, requestUrl, queryText, byte[] bytes,
String contentType, String storageKey, Instant retrievedAt)` — o construtor
canonico nao muda, os chamadores existentes nao mudam.

Datasets de `raw_payload` deste bloco: `hips_properties` (TEXT),
`hips2fits_cutout` (binario), `esa_search_html` (TEXT), `esa_avm_json`
(JSON; se nao for JSON valido, TEXT e candidata rejeitada), `esa_screen_jpg`
(binario).

**3.6 Retencao (correcao obrigatoria).** `RawPayloadRepository.deleteForUnproductiveOnDemandRunsBefore`
so protege payloads referenciados por `measurement.raw_payload_id` — e o
proprio Javadoc diz "nova FK para esta tabela tem de entrar aqui". O
`NOT EXISTS` passa a cobrir tambem `object_image.metadata_payload_id`,
`object_image.download_payload_id` e `hips_survey_snapshot.raw_payload_id`.
Teste de regressao obrigatorio (secao 8).

### 4. Arvore de arquivos — backend

```
backend/src/main/
├── resources/
│   ├── application.yml                       + bloco galaxy.images / galaxy.media (secao 5)
│   ├── application-images.yml                web-application-type: none (como o seed)
│   ├── images/curation.csv                   curadoria manual (secao 6.6), cabecalho + comentario, inicialmente vazia
│   └── db/migration/V8__object_images.sql
└── java/dev/galaxyexplorer/
    ├── catalog/
    │   ├── SourceCode.java                    + CDS_HIPS2FITS, ESA_HUBBLE, ESA_WEBB
    │   ├── position/AngularSeparation.java    puro: separacao angular (formula de Vincenty, citada)
    │   ├── image/
    │   │   ├── ImageKind.java                 enum espelhando o CHECK (dbValue/fromDb), canBeHero()
    │   │   ├── ObjectImageWrite.java          record de gravacao
    │   │   ├── ObjectImageRow.java            record de leitura (+ sourceCode, license name)
    │   │   ├── HeroThumb.java                 record enxuto p/ listas (objectId, thumb sha/dims, kind, credit, acknowledgment, provider, licenseCode)
    │   │   ├── ImageFileRef.java              (storageKey, contentType)
    │   │   ├── ObjectImageRepository.java     upsert, findByObject, findHeroThumbs, applyHero, findFileBySha, deleteForSourceExcept
    │   │   ├── ImageLicense.java + ImageLicenseRepository.java
    │   │   ├── HipsSurveySnapshotRepository.java
    │   │   ├── HeroCandidate.java
    │   │   └── HeroPolicy.java                puro (secao 6.5)
    │   └── read/
    │       ├── ImageView.java                 read model de uma imagem
    │       ├── ImageViews.java                monta ImageView a partir das linhas (tira essa logica de ObjectQueryService)
    │       ├── ImageAbsenceReason.java        enum NO_POSITION, NOT_AVAILABLE (+ messagePt)
    │       ├── ObjectDetail.java              + List<ImageView> images, ImageAbsenceReason imageAbsence
    │       ├── ObjectQueryService.java        + imagens; fontes das imagens entram em `sources`
    │       ├── SearchHit.java                 + HeroThumb thumbnail (pode ser null)
    │       ├── SearchService.java             + 1 consulta em lote findHeroThumbs
    │       ├── CategoryPage.java              Item + HeroThumb thumbnail
    │       └── CategoryQueryService.java      + 1 consulta em lote findHeroThumbs
    ├── media/
    │   ├── MediaProperties.java               @ConfigurationProperties("galaxy.media")
    │   ├── ImageFileStore.java                grava por sha (atomico), resolve por storage key
    │   ├── StoredFile.java                    (storageKey, sha256, byteSize)
    │   ├── JpegInspector.java                 puro sobre bytes: magic FFD8FF, dimensoes via ImageReader SEM decodificar
    │   ├── JpegInfo.java                      (width, height)
    │   └── Thumbnailer.java                   bytes JPEG -> miniatura JPEG (ImageIO, sem dependencia nova)
    ├── ingestion/
    │   ├── connector/
    │   │   ├── RawPayload.java                + ofBinary(...)
    │   │   ├── image/
    │   │   │   ├── ImageTarget.java           objeto-alvo (modelo canonico de entrada)
    │   │   │   ├── ImageCandidate.java        imagem aceita, pronta para gravar (modelo canonico de saida)
    │   │   │   ├── CandidateRejection.java    (sourceImageId, reason)
    │   │   │   ├── ImageDownloader.java       interface
    │   │   │   ├── HttpImageDownloader.java   allowlist, redirect manual, limite de bytes, so JPEG
    │   │   │   ├── DownloadedImage.java       (bytes, contentType, requestUrl, sha256, retrievedAt)
    │   │   │   └── HostAllowlist.java         puro
    │   │   ├── hips2fits/
    │   │   │   ├── HipsSurvey.java            config de um survey (code, hipsId, propertiesUrl, minDecDeg, maxFovDeg)
    │   │   │   ├── HipsPropertiesParser.java  puro: texto `chave = valor` -> HipsSurveyMetadata
    │   │   │   ├── HipsSurveyMetadata.java
    │   │   │   ├── CutoutPlanner.java         puro: alvo -> CutoutPlan (survey, FOV, base)
    │   │   │   ├── CutoutPlan.java
    │   │   │   ├── Hips2FitsUrls.java         puro: monta a URL de recorte (encode de hips/ra/dec)
    │   │   │   ├── Hips2FitsClient.java       interface: properties(survey), cutout(plan)
    │   │   │   └── HttpHips2FitsClient.java
    │   │   └── esa/
    │   │       ├── EsaProvider.java           enum HUBBLE/WEBB: host, searchUrl, avmUrl, screenUrl, facility esperada, SourceCode
    │   │       ├── EsaSearchHtmlParser.java   puro: HTML -> ids de imagem
    │   │       ├── AvmValues.java             puro: desfaz b'…' e "['a', 'b']"
    │   │       ├── AvmJsonParser.java         puro: JSON -> AvmRecord
    │   │       ├── AvmRecord.java
    │   │       ├── SubjectNameMatcher.java    puro: AVM Subject.Name x identificadores do objeto
    │   │       ├── AvmCandidateEvaluator.java puro: AvmRecord + ImageTarget -> aceita/rejeita com motivo
    │   │       ├── EsaLicenseRule.java        puro: Rights literal -> license code (ou rejeita)
    │   │       ├── EsaOutreachClient.java     interface: search(provider, term), avm(provider, id)
    │   │       └── HttpEsaOutreachClient.java
    │   ├── images/
    │   │   ├── ImageProperties.java           @ConfigurationProperties("galaxy.images")
    │   │   ├── ImagesConfig.java              RestClient, limitadores/retry/circuit breaker por fonte, beans dos clientes
    │   │   ├── ImageTargetLoader.java         monta ImageTarget a partir do catalogo
    │   │   ├── ImageIngestionRequest.java
    │   │   ├── ImageIngestionReport.java + ImageOutcome.java
    │   │   ├── CutoutIngestion.java           fluxo do recorte para N alvos (1 run CDS_HIPS2FITS)
    │   │   ├── EsaIngestion.java              fluxo ESA para N alvos (1 run por provider)
    │   │   ├── ImageWriter.java               grava arquivo + linha + hero numa transacao por objeto
    │   │   ├── ImageIngestionService.java     fachada: run(request) -> report
    │   │   ├── ImageSeedRunner.java           profile "images" (comando local, como o seed)
    │   │   └── curation/
    │   │       ├── CurationFile.java          le e valida images/curation.csv
    │   │       └── CurationEntry.java
    │   ├── pipeline/RawPayloadRepository.java  retencao protege payloads de imagem
    │   └── jobs/ImportJobWorker.java           + recorte apos import bem-sucedido
    └── api/
        ├── image/
        │   ├── ImageFileController.java       GET /api/v1/images/files/{sha256}.jpg
        │   ├── ImageDto.java                  imagem completa (detalhe)
        │   ├── ThumbnailDto.java              miniatura (busca/listas)
        │   └── ImageDtoMapper.java            puro: monta DTOs e a URL relativa /api/v1/images/files/<sha>.jpg
        ├── error/ApiErrorCode.java            + IMAGE_NOT_FOUND(404)
        ├── object/ObjectDetailResponse.java   + images, imageAbsence
        ├── object/ObjectDetailMapper.java     + mapeamento (via ImageDtoMapper)
        ├── search/SearchHitDto.java           + thumbnail
        ├── search/SearchController.java       + thumbnail
        ├── category/CategoryObjectsResponse.java  Item + thumbnail
        └── category/CategoryController.java   + thumbnail
```

`.gitignore` (raiz): + `backend/data/` (diretorio padrao das imagens).

### 5. Configuracao (`galaxy.images.*`, `galaxy.media.*`)

```
galaxy.media.storage-dir        default ./data/images (relativo ao diretorio de trabalho do backend; em Docker, volume)
galaxy.media.max-image-bytes    8 MB  (rejeita download maior)
galaxy.media.max-pixels         40_000_000 (protecao contra "bomba de descompressao", checada pelo cabecalho ANTES de decodificar)
galaxy.media.thumb-max-width    320
galaxy.images.user-agent        mesmo texto do SIMBAD
galaxy.images.connect-timeout   10s ; read-timeout 60s (hips2fits com FOV grande demora)
galaxy.images.hips2fits.base-urls  [https://alasky.cds.unistra.fr/hips-image-services/hips2fits,
                                    https://alaskybis.cds.unistra.fr/hips-image-services/hips2fits]   (espelho so em SourceUnavailable)
galaxy.images.hips2fits.requests-per-second  1
galaxy.images.hips2fits.width 800 ; height 600   (4:3, testado ao vivo)
galaxy.images.hips2fits.fov-factor 1.25 ; fov-min-deg 0.1 ; fov-max-deg 5.0 ; fov-default-deg 0.25
galaxy.images.hips2fits.surveys (ordem = preferencia):
   - code PANSTARRS_COLOR, hips-id CDS/P/PanSTARRS/DR1/color-z-zg-g,
     properties-url https://alasky.cds.unistra.fr/Pan-STARRS/DR1/color-z-zg-g/properties,
     min-dec-deg -30, max-fov-deg 1.0
   - code DSS2_COLOR, hips-id CDS/P/DSS2/color,
     properties-url https://alasky.cds.unistra.fr/DSS/DSSColor/properties,
     min-dec-deg -90, max-fov-deg 10
galaxy.images.esa.requests-per-second 1   (um limitador para esahubble+esawebb+cdn: mesma organizacao)
galaxy.images.esa.max-candidates-per-search 8
galaxy.images.esa.max-images-per-object 3  (por provider)
galaxy.images.allowed-hosts  [alasky.cds.unistra.fr, alaskybis.cds.unistra.fr,
                              esahubble.org, cdn.esahubble.org, esawebb.org, cdn.esawebb.org]
```

Limitadores **separados** do limitador `cds` do SIMBAD: o limite de 6 req/s
documentado e do SIMBAD; o hips2fits e outro servico (pesado). Soma no pior
caso (2 + 1) continua abaixo de 6 se o CDS contar tudo junto.

---

## Interfaces/contratos

(`?` = pode ser null. Records imutaveis, listas com `List.copyOf`.)

### 6.1 `ingestion.connector.image`

- `ImageTarget(UUID objectId, String slug, String displayName, List<String> identifiers, Double raDeg?, Double decDeg?, Double angSizeMajorArcmin?, Long angSizeMeasurementId?, String angSizeSource?)` —
  `identifiers` = como gravados (`'M  51'`, `'NAME Whirlpool Galaxy'`, `'NGC  5194'`).
- `ImageCandidate(SourceCode source, String sourceImageId, ImageKind kind, String title?, String provider, String creditVerbatim, String rightsText, String licenseCode, String termsUrl?, String mission?, String instrument?, String wavelength?, String pageUrl, LocalDate releaseDate?, Double centerRaDeg?, Double centerDecDeg?, Double fovDeg?, String fovBasis?, Long derivedFromMeasurementId?, String matchBasis, String downloadUrl, Boolean coversWholeObject?)` —
  importa `catalog.image.ImageKind` e `catalog.SourceCode` (vocabulario comum, mesma excecao deliberada do Bloco 1).
- `CandidateRejection(String sourceImageId, String reason)`
- `DownloadedImage(byte[] bytes, String contentType, String requestUrl, String sha256, Instant retrievedAt)`
- `ImageDownloader`: `DownloadedImage download(URI url, String service)` —
  lanca `SourceUnavailableException` (429/5xx/rede/circuito) ou `SourceQueryException` (4xx, host fora da allowlist, nao-JPEG, grande demais).
- `HttpImageDownloader(RestClient rc, HostAllowlist allow, long maxBytes, RateLimiter rl, CircuitBreaker cb, Retry retry, Clock clock)`:
  - so `https:`; host na allowlist **antes** de cada requisicao;
  - `HttpClient` com `Redirect.NEVER`; 3xx seguido manualmente ate 3 saltos, cada `Location` revalidado na allowlist (fecha SSRF por redirecionamento);
  - le no maximo `maxBytes + 1` (stream limitado; excedeu = rejeita);
  - exige `Content-Type` `image/jpeg` **e** magic `FF D8 FF` (`JpegInspector`).
- `HostAllowlist(Set<String> hosts)`: `boolean allows(URI u)` — comparacao exata de host em minusculas, so https, porta default.

### 6.2 `ingestion.connector.hips2fits`

- `HipsSurvey(String code, String hipsId, URI propertiesUrl, double minDecDeg, double maxFovDeg)`
- `HipsSurveyMetadata(String hipsId, String obsTitle, String obsCopyright, String obsCopyrightUrl?, String hipsLicense, String bibReference?, String creatorDid?)`
- `HipsPropertiesParser.parse(String text): Map<String,String>` e `toMetadata(Map, HipsSurvey): HipsSurveyMetadata` —
  linhas `chave = valor` (trim; `#` = comentario; chave repetida: a ultima vence — **confirmar na fixture real**);
  `obs_copyright` ou `hips_license` ausente → `SourceQueryException` (sem credito/licenca nao ha recorte);
  se o `properties` traz `creator_did` (ou chave equivalente que a fixture mostrar), ele tem de terminar com `hipsId` — prova de que o arquivo de properties e do mesmo HiPS pedido ao hips2fits.
- `CutoutPlan(HipsSurvey survey, double raDeg, double decDeg, double fovDeg, String fovBasis, Long derivedFromMeasurementId?, int width, int height)`
- `CutoutPlanner(List<HipsSurvey> preference, double factor, double minFov, double maxFov, double defaultFov, int w, int h)`:
  `Optional<CutoutPlan> plan(ImageTarget t)` — vazio se sem posicao.
  - FOV: com tamanho angular → `clamp(angSizeMajor/60 × factor, min, max)`, `fovBasis` cita o valor e a fonte; sem → `defaultFov`, `fovBasis` diz "padrão".
    Conferencia com os testes ao vivo: M51 → 0,25° (testado bom), M31 → ~4° (testado bom). **[DECISAO NOVA]** fator 1,25.
  - Survey: o primeiro da lista em que `decDeg − fov/2 ≥ minDecDeg` **e** `fov ≤ maxFovDeg`. PanSTARRS so cobre dec > −30° e satura/fica lento em campo grande; DSS2 cobre o ceu todo. **[DECISAO NOVA]** PanSTARRS preferido ate 1°.
- `Hips2FitsUrls.cutout(URI base, CutoutPlan p): URI` — parametros `hips, ra, dec, fov, width, height, format=jpg` (projecao default do servico; nao inventar parametro). Numeros com `Locale.ROOT`, encode de `hips` (tem `/`).
- `Hips2FitsClient`: `RawPayload properties(HipsSurvey s)` (TEXT, dataset `hips_properties`); `DownloadedImage cutout(CutoutPlan p)` (tenta `base-urls` em ordem so em `SourceUnavailableException`; a URL efetivamente usada vai para `image_url`).

### 6.3 `ingestion.connector.esa`

- `EsaProvider` enum `HUBBLE`, `WEBB`, cada um com:
  `SourceCode source()`, `String providerLabel()` ("ESA/Hubble", "ESA/Webb"),
  `URI searchUrl(String subjectTerm)` (Hubble verificado: `https://esahubble.org/images/archive/search/?adv=&subject_name=M+51`; Webb: **verificar no passo 0** que `https://esawebb.org/images/archive/search/?adv=&subject_name=…` existe),
  `URI avmUrl(String id)` (`https://<host>/images/<id>/api/json/` — verificado para ambos),
  `URI screenUrl(String id)` (Hubble verificado `https://cdn.esahubble.org/archives/images/screen/<id>.jpg`; Webb **verificar** `https://cdn.esawebb.org/archives/images/screen/<id>.jpg`),
  `String expectedFacility()` ("Hubble Space Telescope" verificado; Webb: **copiar o valor exato da fixture** `weic2216b`).
  As URLs ficam em `ImageProperties` (templates), o enum so as compoe.
- `EsaSearchHtmlParser.imageIds(String html): List<String>` — extrai `href="/images/<id>/"` (id `^[a-z0-9]{3,40}$`, rejeita o resto), ordem de aparicao, sem duplicatas, ignora links de navegacao (`/images/archive/...`, `/images/viewall/...`). Nao usar parser HTML novo (sem dependencia); regex ancorada sobre atributos `href`, testada com o HTML real.
- `AvmValues` (puro, **o ponto mais fragil do bloco**):
  - `String text(Object raw)` — `"b'…'"`/`b"…"` (repr de bytes Python) → decodifica escapes `\\xNN` (sequencia de bytes → UTF-8), `\\n`, `\\t`, `\\'`, `\\"`, `\\\\`; string normal → como veio; `null`/vazio → null.
  - `List<String> list(Object raw)` — array JSON real → cada item por `text`; string `"['a', 'b']"` (repr de lista Python) → tokenizador de literais com aspas simples/duplas e escapes (sem `eval`, sem regex gulosa); escalar → lista de 1.
  - Entrada que nao segue nenhuma forma conhecida → `IllegalArgumentException` com o trecho (candidata rejeitada, motivo no relatorio). Nunca "adivinhar".
- `AvmRecord(String id, String type?, String title?, String credit?, String rights?, List<String> subjectNames, List<String> facilities, List<String> instruments, String spectralBand?, Double refRaDeg?, Double refDecDeg?, Double scaleDegPerPx?, Integer refWidthPx?, Integer refHeightPx?, String coordinateFrame?, LocalDate date?, String referenceUrl?)` —
  campos `Spatial.*` como **o JSON real** os nomear (o orquestrador viu `Spatial.ReferenceValue`, `Spatial.Scale`, `Spatial.CoordinateFrame`; `ReferenceDimension`/`ReferencePixel` **so se aparecerem nas fixtures**).
- `AvmJsonParser.parse(String id, String json): AvmRecord` (Jackson 3, `tools.jackson.*`).
- `SubjectNameMatcher.match(List<String> subjectNames, List<String> objectIdentifiers): Optional<String>` (devolve a explicacao) —
  normaliza os dois lados com `IdentifierNormalizer` (o MESMO do catalogo) + uma unica regra de alias: `Messier N` → `M N` (a AVM escreve "Messier 51", o SIMBAD "M  51"; padrao `Messier.DESIGNATION` reaproveitado). `NGC 5194`, `Whirlpool Galaxy` (= `NAME Whirlpool Galaxy`) casam pela normalizacao normal.
- `EsaLicenseRule.licenseCode(String rights): Optional<String>` — igualdade exata (apos trim) com `"Creative Commons Attribution 4.0 International License"` (texto verificado) → `CC-BY-4.0`; qualquer outro texto → vazio = rejeita.
- `AvmCandidateEvaluator.evaluate(EsaProvider p, AvmRecord r, ImageTarget t, boolean curated): Evaluation` (sealed: `Accepted(ImageCandidate)` / `Rejected(String reason)`), na ordem:
  1. `Type`: automatico so `Observation` → `processed_composite`; curado aceita tambem `Artwork` → `artist_impression`, `Chart` → `diagram`; resto rejeita.
  2. `Facility` contem `p.expectedFacility()` (o arquivo da ESA/Hubble tambem publica imagens de contexto de solo, ex. DSS — essas nao sao "ESA/Hubble observando o objeto"; rejeita no automatico).
  3. licenca via `EsaLicenseRule`; `Credit` nao vazio.
  4. `SubjectNameMatcher` casa (automatico e curado: obrigatorio — curadoria escolhe a imagem, nao inventa o vinculo).
  5. Espacial (`AngularSeparation`): exige posicao do objeto e `Spatial.ReferenceValue` com frame ICRS/FK5.
     - com escala + dimensoes → o centro do objeto tem de cair **dentro do quadro** (meia-diagonal); `coversWholeObject = fov ≥ angSizeMajor/60`.
     - sem dimensoes → tolerancia `max(angSizeMajor/2, 5′)`; `coversWholeObject = null` (desconhecido).
     - sem `Spatial` → automatico rejeita ("sem coordenadas na AVM — não verificável"); curado aceita com `match_basis` dizendo isso.
     **[DECISAO NOVA]** tolerancias.
  6. `downloadUrl = p.screenUrl(id)` (nunca `ResourceURL`, o TIF gigante).
  `matchBasis` monta a frase com os valores usados (nome casado, separacao em arcmin, tolerancia).
- `EsaOutreachClient`: `RawPayload search(EsaProvider p, String term)` (TEXT, dataset `esa_search_html`); `RawPayload avm(EsaProvider p, String id)` (JSON, dataset `esa_avm_json`; content-type `text/plain` aceito — fato verificado).

### 6.4 Credito: texto literal

`credit_verbatim` = `AvmValues.text(Credit)`. Se a fixture real mostrar
marcacao HTML no `Credit` (algumas paginas da ESA linkam nomes), grava-se o
**texto** (tags removidas, entidades decodificadas, palavras intactas) e o
original continua no `raw_payload`. Sem marcacao (caso verificado em
`heic0506a`) → identico. Frontend renderiza sempre como texto.

### 6.5 `catalog.image`

- `ImageKind` enum `OBSERVATION, SURVEY_CUTOUT, PROCESSED_COMPOSITE, ARTIST_IMPRESSION, DIAGRAM` — `dbValue()`, `fromDb()`, `boolean canBeHero()`.
- `ObjectImageWrite` — todas as colunas gravaveis de 3.3 + `curatedBy ('automatic'|'manual')`, `runId`, `metadataPayloadId?`, `downloadPayloadId`, `StoredFile display`, `StoredFile thumb`, dims.
- `ObjectImageRepository`:
  - `long upsert(UUID objectId, short sourceId, ObjectImageWrite w)` — `ON CONFLICT (object_id, source_id, source_image_id) DO UPDATE` (nao toca `is_hero`);
  - `int deleteForSourceExcept(UUID objectId, short sourceId, Set<String> keepIds, String curatedBy)` — imagem que a selecao automatica deixou de aceitar sai (nao fica fantasma); nunca apaga `manual` numa rodada automatica;
  - `List<ObjectImageRow> findByObject(UUID objectId)` — ordenado `is_hero DESC, gallery_rank, id`;
  - `Map<UUID, HeroThumb> findHeroThumbs(Collection<UUID> objectIds)` — **uma** consulta (sem N+1);
  - `void applyHero(UUID objectId, Long imageId?)` — zera e marca na mesma transacao (ordem importa pelo indice parcial, como `applyPreferred`);
  - `Optional<ImageFileRef> findFileBySha(String sha)` — `(storageKey, contentType)` de `content_sha256` **ou** `thumb_sha256`.
- `HeroCandidate(long id, ImageKind kind, String sourceCode, String curatedRole?, Boolean coversWholeObject, LocalDate releaseDate?, Double separationArcmin?)`
- `HeroPolicy.choose(List<HeroCandidate>): Optional<Long>` (puro), ordem documentada **[DECISAO NOVA]**:
  1. so `kind.canBeHero()`;
  2. curadoria `hero` vence tudo;
  3. `processed_composite` antes de `survey_cutout` (imagem da ESA, quando valida, e mais informativa que o recorte);
  4. `coversWholeObject = true` antes de desconhecido antes de `false` (evita hero que e um detalhe minusculo do objeto);
  5. `releaseDate` mais recente;
  6. empate: ESA/Webb antes de ESA/Hubble;
  7. menor separacao; 8. menor id.
  `gallery_rank` segue a mesma ordem para as demais.
- `ImageLicenseRepository.findByCode(String)`; `HipsSurveySnapshotRepository.insert(...)`.
- `catalog/position/AngularSeparation.degrees(ra1, dec1, ra2, dec2)` — formula de Vincenty (estavel para angulos pequenos); teste com identidades geometricas (polo, mesmo ponto, 1° em dec) e com as posicoes reais das fixtures.

### 6.6 [DECISAO NOVA] Curadoria manual — `resources/images/curation.csv`

Colunas: `object_query,provider,image_id,action,note` — `provider` ∈
`HUBBLE|WEBB`; `action` ∈ `include|hero|exclude`. Arquivo versionado
(revisado no diff, sem migracao a cada ajuste editorial). Linhas comecando
com `#` sao comentario. `CurationFile` valida tudo antes de rodar (id no
formato, provider valido, objeto resolvido **localmente** pela busca exata;
erro = aborta o comando com a lista de problemas). `include`/`hero` passam
pelo `AvmCandidateEvaluator` com `curated=true` — **os metadados sempre vem da
AVM**; curadoria escolhe, nunca descreve. `exclude` impede a selecao
automatica de aceitar aquele id para aquele objeto.
Alternativa vetavel: tabela `image_curation` alimentada por migracoes.

### 6.7 `media`

- `ImageFileStore`: `StoredFile store(byte[] bytes)` — sha256 → `ab/<sha>.jpg`; se existe, nao regrava (conteudo identico); grava em temporario no mesmo diretorio + `Files.move(ATOMIC_MOVE)`. `Optional<Path> resolve(String storageKey)` — normaliza e exige que o caminho final esteja **dentro** de `storageDir` (defesa em profundidade; a chave vem do banco, nunca da URL).
- `JpegInspector.inspect(byte[]): JpegInfo` — magic + dimensoes pelo cabecalho (`ImageReader.getWidth/Height`), sem decodificar pixels; `width×height > maxPixels` → rejeita.
- `Thumbnailer.thumbnail(byte[] jpeg, int maxWidth): byte[]` — so roda depois do `JpegInspector`; reducao em etapas com interpolacao bicubica; saida JPEG (qualidade fixa documentada); metadados nao copiados. Miniatura e derivada, nao e "a imagem da fonte" — por isso `thumb_url` NULL e o `image_url` aponta a origem do original.

### 6.8 `ingestion.images` (orquestracao)

- `ImageIngestionRequest(Set<ImageSource> sources, List<UUID> objectIds /* vazio = todos */, RunTrigger trigger, boolean refresh)`; `ImageSource` enum `CUTOUT, ESA_HUBBLE, ESA_WEBB`.
- `ImageOutcome(UUID objectId, String slug, ImageSource source, Kind kind, String detail)`; `Kind` = `STORED, UNCHANGED, NO_POSITION, NO_VALID_CANDIDATE, SOURCE_UNAVAILABLE, SOURCE_QUERY_FAILED, WRITE_FAILED`.
- `ImageIngestionReport(Map<ImageSource, Long> runIds, List<ImageOutcome> outcomes)` + `List<String> cutoutOnlySlugs()` (alvos sem imagem ESA valida — e a lista "so recorte" real, impressa pelo runner).
- `ImageTargetLoader.load(List<UUID> ids): List<ImageTarget>` — posicao preferida (`PositionRepository.findPreferred`), `ang_size_major` preferida (`MeasurementRepository.findByObjectAndProperty`), identificadores.
- `CutoutIngestion.run(List<ImageTarget>, RunTrigger, boolean refresh): (runId, outcomes)` — abre run `CDS_HIPS2FITS` (connector_version `hips2fits/1`); busca `properties` de cada survey usado **uma vez por run** (grava payload + snapshot); para cada alvo: plano → download → `ImageWriter`. `refresh=false` e o alvo ja tem recorte do mesmo survey → `UNCHANGED` sem requisicao.
- `EsaIngestion.run(EsaProvider, List<ImageTarget>, CurationFile, RunTrigger, boolean refresh)` — um run por provider (`esa-avm/1`). Para cada alvo:
  termo de busca = designacao Messier (`M 51`, como no exemplo verificado) **[DECISAO NOVA]**; sem Messier → primeiro identificador `NGC`; sem nenhum → pula ESA (`NO_VALID_CANDIDATE`, "sem designação M/NGC para buscar");
  busca → ids (ate `max-candidates`) → AVM de cada (AVM de um id ja buscada **neste run** nao e rebuscada) → avaliador → aceitas ordenadas por `HeroPolicy` → ate `max-images-per-object` baixadas → `ImageWriter`. Rejeicoes vao para o `error_summary` como "M 51 / heic0506b: Facility sem Hubble Space Telescope".
- `ImageWriter.write(ImageTarget t, ImageCandidate c, DownloadedImage d, long runId, Long metadataPayloadId, ...)`:
  fora da transacao: `JpegInspector` → `ImageFileStore.store` (original e miniatura) → `RawPayload.ofBinary` gravado;
  dentro de `TransactionTemplate` (uma por objeto): `upsert`, `deleteForSourceExcept`, `HeroPolicy` sobre **todas** as imagens do objeto, `applyHero`, `gallery_rank`.
  Arquivo sem linha (transacao falhou) = orfao inofensivo (conteudo enderecado; ver R9).
- `ImageIngestionService.run(ImageIngestionRequest): ImageIngestionReport` — fachada; `ingestCutoutsAfterImport(List<UUID> ids)` para o worker de importacao (so recorte, `ON_DEMAND`, nunca lanca: falha vira log + outcome).
- `ImageSeedRunner` (`@Profile("images")`, como `MessierSeedRunner`):
  `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=images" "-Dspring-boot.run.arguments=--galaxy.images.run.sources=CUTOUT,ESA_HUBBLE,ESA_WEBB --galaxy.images.run.objects=all"`;
  `objects` aceita `all` ou lista de termos (resolvidos pela busca exata local); imprime relatorio, contagens por `Kind` e **a lista de objetos que ficaram so com recorte**; exit 1 se algum run `failed`.
- `ImportJobWorker`: apos `SUCCEEDED` **com objeto novo gravado** (nao no caminho "já existia no catálogo"), chama `ingestCutoutsAfterImport(written)` **antes** de `markFinished`, dentro do status `running` (o frontend ja mostra "importando…"). Falha do recorte **nunca** muda o status do job (fica `succeeded`; detalhe no log/run). Custo: +1 requisicao ao CDS por objeto importado (~1–10 s). ESA nao roda sob demanda (ate ~20 requisicoes por objeto; fica para o comando `images`). **[DECISAO NOVA]**

### 6.9 Contrato da API

**`GET /api/v1/objects/{slug}`** — `ObjectDetailResponse` ganha dois campos (os demais intactos):

```
images: Image[]                        // hero primeiro, depois gallery_rank; [] quando nao ha
imageAbsence: {code, message} | null   // nao-null se e somente se images = []
```
`code` ∈ `NO_POSITION` ("Imagem não disponível: objeto sem posição catalogada — não há como gerar um recorte") | `NOT_AVAILABLE` ("Imagem não disponível: nenhuma imagem com crédito e licença verificados foi obtida para este objeto"). As fontes das imagens (`CDS_HIPS2FITS`, `ESA_*`) entram em `sources[]` (o acknowledgment do hips2fits aparece na secao Fontes).

`Image` (`api/image/ImageDto`):
```
id, kind, hero (bool), url, width, height,          // url = "/api/v1/images/files/<sha>.jpg" (relativa, mesma origem)
thumbnail: {url, width, height},
title|null, provider, source (code), credit, acknowledgment|null,   // acknowledgment = source.acknowledgment (hips2fits)
license: {code, name, url}, rightsText, termsUrl|null,
mission|null, instrument|null, wavelength|null,
pageUrl, sourceImageId, releaseDate|null (data ISO),
cutout: {survey, hipsId, fovDeg, fovBasis, centerRaDeg, centerDecDeg} | null,
matchBasis, curatedBy ('automatic'|'manual'), retrievedAt
```

**`GET /api/v1/search`** — `SearchHitDto` + `thumbnail: Thumbnail | null`.
**`GET /api/v1/categories/{code}/objects`** — `Item` + `thumbnail: Thumbnail | null`.
`Thumbnail` (`api/image/ThumbnailDto`) = miniatura do **hero**:
`{url, width, height, kind, provider, credit, acknowledgment|null, licenseCode}`.
Uma consulta em lote por resposta (`findHeroThumbs`).

**`GET /api/v1/images/files/{sha256}.jpg`** (`ImageFileController`):
- path fora de `^[0-9a-f]{64}\.jpg$` → 404 `IMAGE_NOT_FOUND` **sem** consultar banco nem disco;
- sha que nao esta em `object_image` (display ou thumb) → 404 (nunca serve arquivo que o banco nao conhece);
- linha existe mas o arquivo sumiu → 404 + log ERROR (inconsistencia);
- 200: `Content-Type: image/jpeg`, `Cache-Control: public, max-age=31536000, immutable`, `ETag: "<sha>"` (304 em `If-None-Match`), `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`, `Cross-Origin-Resource-Policy: same-origin`, `Content-Security-Policy: default-src 'none'`;
- o caminho do arquivo vem **so** do banco (`storage_key`) via `ImageFileStore.resolve`.

---

## 7. Frontend

### 7.1 Arvore (so o que muda)

```
frontend/
├── index.html                  + <meta http-equiv="Content-Security-Policy" content="img-src 'self' data: blob:">
├── scripts/capture-fixtures.mjs + fase "refresh" (re-GET de todas as fixtures sem efeito colateral) e estados de imagem
└── src/
    ├── api/types.ts            + ObjectImage, ImageKind, ImageThumbnail, ImageAbsence, ImageLicense, CutoutInfo; campos novos; 'IMAGE_NOT_FOUND'
    ├── domain/
    │   ├── imageKind.ts        kind -> {label, description, variant}; desconhecido -> codigo cru + "tipo não catalogado"
    │   ├── imageAlt.ts         alt honesto: "<displayName> — <rotulo do kind> (<provider>)" (nao descreve conteudo que nao conhecemos)
    │   └── imageSrc.ts         safeImageSrc(url): so aceita ^/api/v1/images/files/[0-9a-f]{64}\.jpg$, senao null
    ├── components/ui/ProgressiveImage.tsx (+ .module.css)
    ├── features/image/
    │   ├── ObjectHero.tsx       <figure> com a imagem selecionada + ImageCaption
    │   ├── ImageCaption.tsx     credito literal, acknowledgment, licenca, missao/instrumento, data de publicacao, link original, selo
    │   ├── ImageKindBadge.tsx
    │   ├── ImageGallery.tsx     so quando images.length > 1
    │   ├── ImageProvenance.tsx  <details> "Como esta imagem foi escolhida": matchBasis, fovBasis, sourceImageId, retrievedAt
    │   ├── ImageUnavailable.tsx substitui ImagePlaceholder; texto = imageAbsence.message
    │   ├── ResultThumbnail.tsx  miniatura + credito para busca e listas
    │   └── Image.module.css
    ├── features/object/ImagePlaceholder.tsx   REMOVIDO (um componente de ausencia so)
    ├── features/search/SearchResultItem.tsx   + ResultThumbnail
    ├── features/category/ObjectList.tsx       + ResultThumbnail
    └── pages/ObjectPage.tsx                   ObjectHero ou ImageUnavailable no lugar de ImagePlaceholder
```

### 7.2 Tipos (`api/types.ts`)

`ImageKind = 'observation'|'survey_cutout'|'processed_composite'|'artist_impression'|'diagram'|(string & {})`;
`ObjectImage`, `ImageThumbnail`, `ImageAbsence {code: 'NO_POSITION'|'NOT_AVAILABLE'|(string & {}); message}` espelhando 6.9 campo a campo;
`ObjectDetail.images: ObjectImage[]`, `ObjectDetail.imageAbsence: ImageAbsence | null`,
`SearchHit.thumbnail` e `CategoryObjectItem.thumbnail: ImageThumbnail | null`.

### 7.3 Componentes

| Componente | Props | Regra |
|---|---|---|
| `ProgressiveImage` | `{src, width, height, alt, placeholderSrc?, loading: 'eager'\|'lazy', fetchPriority?}` | Wrapper com `aspect-ratio: width / height` + `<img width height>` → **zero layout shift**. Placeholder = a miniatura num segundo `<img>` desfocado por baixo (nao CSS `url()` com string da API); fade ao carregar, desligado em `prefers-reduced-motion`. `src` passa por `safeImageSrc`; null ou `onError` → estado "Falha ao carregar a imagem" (o credito continua visivel). |
| `ObjectHero` | `{images, displayName}` | Estado local `selected` (comeca no `hero`). Hero: `loading="eager"`, `fetchPriority="high"`, `decoding="async"`. Abaixo: `ImageCaption`; se `images.length > 1`, `ImageGallery`. |
| `ImageCaption` | `{image}` | **Credito literal sempre visivel** (`credit` + linha do `acknowledgment` se houver), `white-space: pre-line`, nunca truncado. `ImageKindBadge`. Licenca: `ExternalLink(license.url, license.name)`. `termsUrl` via `ExternalLink` (se for `http:`, o `safeUrl` ja exibe como texto). Missao/instrumento se houver. "Data de publicação" (nao "observação"). "Página original" via `ExternalLink(pageUrl)` (recorte: "Refazer este recorte no CDS"). `ImageProvenance`. |
| `ImageKindBadge` | `{kind}` | Texto + icone (nunca so cor). `artist_impression` → "Ilustração artística — não é fotografia", tambem sobreposto na propria imagem. |
| `ImageGallery` | `{images, selectedId, onSelect}` | Lista de `<button aria-pressed>` com miniaturas (`loading="lazy"`, width/height) e selo pequeno; teclado nativo. |
| `ImageUnavailable` | `{absence}` | "Imagem não disponível" + `absence.message`. Sem desenho que pareca o objeto (aneis abstratos `aria-hidden`, como hoje). Mesmo espaco reservado do hero (4:3) → sem salto. |
| `ResultThumbnail` | `{thumbnail, displayName}` | Miniatura (`loading="lazy"`, width/height) + `ImageKindBadge` compacto + **credito literal** em fonte pequena (quebra linha, sem reticencias) + codigo da licenca em texto. Sem `<a>` interno (o item ja e `<Link>`; link aninhado e HTML invalido); o link da licenca fica na pagina do objeto. `thumbnail: null` → bloco neutro "sem imagem" do mesmo tamanho. |

**[DECISAO NOVA] Credito nas miniaturas:** leitura estrita do pedido — credito
literal visivel tambem nas listas. Alternativa vetavel (CC BY 4.0 aceita link
para a atribuicao): so `provider` + "crédito completo na página do objeto".

### 7.4 CSP de imagem — **[DECISAO NOVA]**

`<meta http-equiv="Content-Security-Policy" content="img-src 'self' data: blob:">`
no `index.html`. So restringe imagens (nao quebra o HMR nem o preambulo do
Vite). E o enforcement de navegador da §12 para imagens; um teste le o
`index.html` e confere a diretiva.

---

## 8. Testes (fixtures reais, nada inventado)

### 8.1 Fixtures a capturar no passo 0

Em `backend/src/test/resources/fixtures/`. O README registra URL, data UTC,
status, Content-Type e o comando `curl.exe`, a 1 req/s.

| arquivo | origem |
|---|---|
| `hips2fits/dss2_color.properties`, `hips2fits/panstarrs_color.properties` | URLs de properties da secao 5 |
| `hips2fits/m51_panstarrs_160x120.jpg` + `.url` | recorte real pequeno (M51, PanSTARRS, fov 0.25, 160×120) — o "JPG pequeno real" |
| `hips2fits/error_bad_hips.txt` + status no README | resposta real a um `hips=` inexistente |
| `esa/hubble_search_m51.html`, `esa/webb_search_m16.html` | busca por `subject_name` |
| `esa/hubble_avm_heic0506a.json`, `esa/webb_avm_weic2216b.json` | AVM verificadas pelo orquestrador |
| `esa/hubble_avm_<id>.json` (≥3) | (a) uma candidata do HTML de M51 que **nao** e M51; (b) uma `Artwork` real; (c) uma imagem de contexto sem "Hubble Space Telescope" em `Facility` — achar ids reais no arquivo, nunca montar JSON |
| `simbad/m16_*` + `sesame/m16.*` | M16 nao esta nas fixtures atuais; necessario para o teste com `weic2216b` |
| cabecalhos de `HEAD` de `cdn.esahubble.org/.../screen/heic0506a.jpg` e `cdn.esawebb.org/.../screen/weic2216b.jpg` | so no README (confirma o template; o JPG grande nao entra no repositorio) |

`Fixtures.readBytes(name)` novo. Teste de falha altera **em memoria** um corpo
real e declara o que alterou (regra ja vigente).

### 8.2 Backend

- Unitarios puros: `AvmValuesTest` (todas as formas vistas nas fixtures reais), `AvmJsonParserTest`, `EsaSearchHtmlParserTest`, `SubjectNameMatcherTest`, `EsaLicenseRuleTest`, `AvmCandidateEvaluatorTest` (aceita heic0506a×M51 e weic2216b×M16; rejeita (a)(b)(c) com o motivo certo; `Artwork` curado → `artist_impression`), `HipsPropertiesParserTest`, `CutoutPlannerTest` (tamanhos angulares reais das fixtures SIMBAD; sem tamanho → padrao; dec < −30 → DSS2), `Hips2FitsUrlsTest`, `AngularSeparationTest`, `HeroPolicyTest`, `JpegInspectorTest`/`ThumbnailerTest` (JPG real), `ImageFileStoreTest` (`@TempDir`, atomico, idempotente, `resolve` recusa `..`).
- `HttpImageDownloaderTest` (`MockRestServiceServer`, como `HttpSesameClientTest`): host fora da allowlist (nenhuma requisicao feita), 302 para host fora → rejeita, 302 dentro → segue, acima de max bytes, `text/html`, magic errado, 503 → retry + `SourceUnavailable`, sucesso com o JPG real.
- Integracao (`IntegrationTestBase`, storage em `@TempDir` via `@DynamicPropertySource`, clientes de fixture `FixtureHips2FitsClient`/`FixtureEsaClient` — requisicao sem fixture = excecao):
  - `MigrationIntegrationTest`: V8 aplicada; hero + `artist_impression` recusado pelo CHECK; segundo hero recusado pelo indice;
  - `ImageIngestionIntegrationTest`: M51 (ingerido das fixtures `cz_*`) → 1 recorte + heic0506a; hero = ESA; arquivos com o sha certo no disco; payloads com os datasets certos; rerun sem `refresh` = `UNCHANGED`, sem linhas novas; `exclude` na curadoria remove; objeto sem posicao → `NO_POSITION`;
  - `ObjectApiIntegrationTest`/`SearchApiIntegrationTest`/`CategoryApiIntegrationTest`: `images`, `imageAbsence`, `thumbnail`, fonte `CDS_HIPS2FITS` em `sources`;
  - `ImageFileControllerIntegrationTest`: cabecalhos, 304, sha desconhecido 404, formato invalido 404, miniatura servida;
  - `ImportRetentionIntegrationTest`: payloads referenciados por `object_image`/`hips_survey_snapshot` sobrevivem;
  - `ImportJobWorkerTest`: recorte chamado apos objeto novo; excecao no recorte → job continua `succeeded`; "já existia" → nao chama.

### 8.3 Frontend

- Fixtures: **todas** as `object-*`, `search-*` e `category-*` mudam de formato → recapturar (fase `refresh`: re-GET pelos mesmos caminhos, sem import novo) depois que o comando `images` rodar no banco de dev. Novas: `object-messier-51` (hero ESA + galeria), `object-messier-16` (Webb, se aceito), um objeto so com recorte (esperado: um aglomerado aberto, ex. `messier-45` — confirmar), `search-m31` e listas com `thumbnail`. `coverage.md` ganha os estados de imagem (`image-processed-hero`, `image-cutout-only`, `image-gallery`, `image-artist`, `image-absent-NO_POSITION`, `image-absent-NOT_AVAILABLE`, `thumb-null`); estado sem exemplo real fica como gap, sem teste com dado inventado.
- Testes: `imageKind`, `imageAlt`, `safeImageSrc`; `ObjectHero` com `object-messier-51` (credito **igual** ao da fixture, link da licenca, selo, troca pela galeria); objeto so com recorte (acknowledgment do hips2fits visivel; `termsUrl` http como texto); `ImageUnavailable` com fixture real se houver; `SearchResultItem`/`ObjectList` com miniatura e com `thumbnail: null`; um teste que renderiza **todas** as fixtures de objeto e afirma que todo `img[src]` comeca com `/api/v1/images/files/`; teste do CSP no `index.html`.

---

## 9. Sequencia de implementacao (cada passo verificavel)

0. **Verificacoes ao vivo + fixtures** (8.1): templates do Webb (busca, `screen`), valor exato de `Facility` do Webb, chaves reais do `properties` (`creator_did`?), presenca de `Spatial.ReferenceDimension` nas AVM, URLs e textos de `source`/`image_license`. Verifica: arquivos + README preenchidos.
1. V8 + `SourceCode` → `.\mvnw.cmd test -Dtest=MigrationIntegrationTest,ReferenceDataIntegrationTest`.
2. `media` + `AngularSeparation` + parsers/regras puras (6.2, 6.3, 6.5) com testes → `.\mvnw.cmd test` verde.
3. `HttpImageDownloader`, `HttpHips2FitsClient`, `HttpEsaOutreachClient`, `ImagesConfig` + testes.
4. Repositorios de `catalog.image`, `ImageWriter`, `CutoutIngestion`, `ImageSeedRunner` + integracao. **Ao vivo**: `-Dspring-boot.run.profiles=images --galaxy.images.run.sources=CUTOUT`. Verifica por SQL: nº de `survey_cutout` = nº de objetos com posicao preferida; abrir 3 recortes (M31, M51, M57) e conferir a olho.
5. `EsaIngestion` + curadoria. **Ao vivo** com `ESA_HUBBLE,ESA_WEBB`. Verifica: relatorio com a lista "so recorte"; SQL: nenhum hero `artist_impression`/`diagram`; revisar o `match_basis` de 5 imagens aleatorias.
6. Leitura + API + `ImageFileController` + testes; `curl.exe -I` num arquivo servido confere os cabecalhos.
7. Retencao (3.6) + teste.
8. `ImportJobWorker` + teste; ao vivo, importar um objeto novo e conferir o recorte.
9. Frontend: tipos, `domain`, componentes, CSP, recaptura, testes → `npm run typecheck && npm run lint && npm test`; no navegador, a aba Network nao mostra nenhum host externo e nao ha layout shift no carregamento.
10. Revisao: `takumi`, `hattori`, `yagyu` (obrigatorio: download/SSRF, servico de arquivo, CSP).

---

## 10. Riscos apontados

- **R1 Parser AVM (`b'…'`, listas como string)** e fragil. Mitigacao: so formas vistas nas fixtures reais; forma desconhecida → rejeita com motivo, nunca adivinha.
- **R2 HTML de busca da ESA muda** (ou o Webb nao tem a mesma busca). Efeito: 0 candidatos → so recorte, sem erro silencioso: o runner avisa quando a busca devolve 0 ids para 100% dos alvos ("provável mudança de HTML").
- **R3 Falso positivo de assunto** (a AVM lista M51 numa imagem centrada na galaxia companheira). Mitigado pela checagem espacial; o `match_basis` deixa auditar.
- **R4 Termos do DSS alem do ODbL** (placas POSS/UK Schmidt, STScI) e politica do PS1. Para projeto pessoal nao comercial, credito + licenca + link de termos atendem; **o usuario deve ler o `obs_copyright_url`** antes de publicar o site. O `obs_copyright_url` do DSS e `http:` → exibido como texto.
- **R5 Hero parcial em objeto grande** (M31, M33, M42, M8): a imagem da ESA pode ser so um pedaco do disco. A politica poe `coversWholeObject=true` na frente, mas `processed_composite` ainda vence o recorte. Alternativa vetavel: o recorte vence a ESA quando `coversWholeObject=false`.
- **R6 Seguranca do download** (SSRF via redirect, bomba de descompressao, arquivo enorme, tipo falso): allowlist + redirect manual + limite de bytes/pixels + magic. Revisao do `yagyu`.
- **R7 Acoplamento de leitura**: `SearchService`/`CategoryQueryService` passam a depender de `ObjectImageRepository` (uma consulta em lote cada); a montagem de imagens fica em `ImageViews` para nao inchar o `ObjectQueryService`.
- **R8 Tempo do lote ESA**: ~110 × 2 × (1 + 8 + 3) ≈ 2.600 requisicoes a 1 req/s ≈ 45 min na primeira vez; reexecutar sem `refresh` quase nao faz requisicao.
- **R9 Arquivos orfaos** (a transacao falhou depois de gravar o arquivo, ou a imagem foi substituida): inofensivos (enderecados por conteudo); a limpeza fica fora deste bloco.
- **R10 Latencia do import**: +1 recorte (ate o read-timeout de 60 s). Circuit breaker proprio do hips2fits; o job nunca falha por causa da imagem.
- **R11 O contrato muda** → todas as fixtures do frontend sao recapturadas; o `tsc` sobre `fixtures/index.ts` pega divergencia.
- **R12 Credito com HTML na AVM** (6.4) e credito longo nas listas (7.3): o layout precisa aguentar 2–3 linhas.
- **R13 Templates do Webb nao verificados** (busca, `screen`, `Facility`): passo 0; se diferirem, muda so a configuracao.
- **R14 `raw_payload` com HTML grande** em jsonb (string); caractere NUL nao cabe em jsonb → payload rejeitado com motivo (nao trunca em silencio).
- **R15 Sobreposicao**: `ExternalLink` continua sendo o unico `<a>` externo; `ImagePlaceholder` e removido para nao existirem dois componentes de ausencia; `IdentifierNormalizer` e reaproveitado (nenhuma segunda normalizacao).

### Messier que provavelmente ficarao so com recorte

**Estimativa, nao verificada** — a lista real sai do relatorio do passo 5. O
campo do Hubble tem ~3′ e o do Webb ~2′; aglomerados abertos ocupam de 10′ a
mais de 1° e quase nunca saem em release da ESA. Provaveis: os aglomerados
abertos M6, M7, M11, M18, M21, M23, M25, M26, M29, M34, M35, M36, M37, M38,
M39, M41, M44, M45, M46, M47, M48, M50, M52, M67, M93, M103; mais M24 (nuvem
estelar), M40 (estrela dupla) e M73 (asterismo). Fora dos Messier, estrelas
como Betelgeuse tendem a ficar so com recorte: o que a ESA publica delas
costuma ser `Artwork`, que a selecao automatica recusa.

---

## 11. Resumo das [DECISAO NOVA] para o usuario aprovar

1. **Hospedar** as imagens (download na ingestao, servidas pelo backend), nao hot-link (§1).
2. CSP `img-src 'self' data: blob:` no `index.html` (§7.4).
3. Imagens da ESA = `processed_composite`; `observation` fica reservado (§2).
4. Bytes em disco + `raw_payload` binario por referencia (`RawPayload.ofBinary`) (§3.5).
5. Tabelas novas `image_license` e `hips_survey_snapshot`; colunas novas em `object_image` (§3).
6. FOV = 1,25 × tamanho angular (0,1°–5°; padrao 0,25°); PanSTARRS ate 1° e dec ≥ −30°, senao DSS2 (§6.2).
7. Filtros da ESA: so `Observation` no automatico, `Facility` da missao, licenca CC BY 4.0 exata, nome + checagem espacial com tolerancias (§6.3).
8. Ordem do hero: curadoria > ESA > cobertura total > mais recente > Webb > Hubble (§6.5; alternativa em R5).
9. Curadoria em `images/curation.csv` versionado (alternativa: tabela via migracao) (§6.6).
10. Termo de busca da ESA = designacao Messier, depois NGC (§6.8).
11. O import sob demanda gera so o recorte, sincrono e tolerante a falha; a ESA so roda pelo comando `images` (§6.8).
12. Credito literal tambem nas miniaturas (alternativa: provider + link) (§7.3).

---

## ADENDO (orquestrador, 2026-10-07) — decisões do usuário. PREVALECE.

1. **Hospedar** no backend (não hot-link) — aprovado.
2. Importação sob demanda: **só o recorte hips2fits**; ESA só pelo comando `images` — aprovado.
3. Curadoria em **`images/curation.csv`** versionado — aprovado.
4. **Hero:** ESA/Hubble/Webb por padrão, **exceto** quando o quadro da imagem ESA cobre bem menos que o tamanho angular do objeto (objetos grandes como M31, M42): aí o recorte hips2fits vira hero e a ESA vai para a galeria. Defina o limiar explicitamente (ex.: lado menor do quadro ESA < 50% de ang_size_major) e teste com dados reais.
5. **Crédito nas miniaturas:** nome curto do provedor ("ESA/Hubble", "ESA/Webb", "DSS2 via CDS", "PanSTARRS via CDS"); crédito literal completo SEMPRE visível junto da imagem na página do objeto (hero e galeria).
6. Demais [DECISAO NOVA] da §11: aceitas como propostas.
7. Migrações novas começam em V8; nunca alterar V1–V7.
