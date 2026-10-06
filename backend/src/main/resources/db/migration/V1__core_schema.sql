-- V1: esquema central aprovado em docs/00-fase0-arquitetura.md, secao 6,
-- copiado literalmente. Unica adicao: a extensao pg_trgm, exigida pelo indice
-- gin_trgm_ops de object_identifier. (gen_random_uuid() e nativo desde o PG 13.)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

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
