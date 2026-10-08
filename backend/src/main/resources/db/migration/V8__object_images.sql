-- V8: imagens (Bloco 3) — recortes CDS hips2fits + imagens ESA/Hubble e ESA/Webb.
--
-- object_image existe desde a V1 e nunca foi gravada. Esta migracao a ESTENDE
-- (ALTER TABLE), nao a recria. Colunas NOT NULL sem default so funcionam com a
-- tabela vazia: a guarda abaixo falha alto em vez de inventar valor.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM object_image) THEN
    RAISE EXCEPTION 'V8: object_image tem linhas; a migracao assume tabela vazia (colunas NOT NULL sem default)';
  END IF;
END
$$;

-- ── Fontes ───────────────────────────────────────────────────────────────
-- URLs verificadas ao vivo em 2026-10-07 (fixtures/README.md, secao Bloco 3).
-- CDS_HIPS2FITS: texto da secao "Acknowledging HiPS2FITS" da pagina do servico,
-- sem a marcacao LaTeX \footnote{...} (o conteudo dela e a propria homepage_url).
-- ESA_*: credito e por imagem (AVM Credit), por isso acknowledgment NULL.
INSERT INTO source (code, name, homepage_url, terms_url, acknowledgment) VALUES
  ('CDS_HIPS2FITS',
   'CDS hips2fits',
   'https://alasky.cds.unistra.fr/hips-image-services/hips2fits',
   'https://cds.unistra.fr/legals/',
   'This research made use of hips2fits, a service provided by CDS.'),
  ('ESA_HUBBLE', 'ESA/Hubble', 'https://esahubble.org/', 'https://esahubble.org/copyright/', NULL),
  ('ESA_WEBB',   'ESA/Webb',   'https://esawebb.org/',   'https://esawebb.org/copyright/',   NULL);

-- ── Licencas (como dado) ─────────────────────────────────────────────────
-- Nome = titulo da pagina oficial da licenca (2026-10-07). Licenca fora desta
-- tabela = imagem rejeitada (nunca presumida).
CREATE TABLE image_license (
  code text PRIMARY KEY,
  name text NOT NULL,
  url  text NOT NULL
);
INSERT INTO image_license (code, name, url) VALUES
  ('CC-BY-4.0', 'Creative Commons Attribution 4.0 International', 'https://creativecommons.org/licenses/by/4.0/'),
  ('ODbL-1.0',  'Open Data Commons Open Database License (ODbL) v1.0', 'https://opendatacommons.org/licenses/odbl/1-0/');

-- ── Proveniencia do credito dos recortes ─────────────────────────────────
CREATE TABLE hips_survey_snapshot (
  id                bigserial PRIMARY KEY,
  hips_id           text NOT NULL,                 -- 'CDS/P/DSS2/color'
  properties_url    text NOT NULL,
  obs_title         text,                          -- literais do arquivo properties
  obs_copyright     text,
  obs_copyright_url text,
  hips_license      text,
  hips_copyright    text,                          -- titular da licenca do HiPS (!= obs_copyright, credito dos dados)
  bib_reference     text,
  raw_payload_id    bigint NOT NULL REFERENCES raw_payload,
  retrieved_at      timestamptz NOT NULL
);

-- ── object_image ─────────────────────────────────────────────────────────
ALTER TABLE object_image
  ADD COLUMN source_id          smallint NOT NULL REFERENCES source,
  ADD COLUMN source_image_id    text NOT NULL,
  ADD COLUMN run_id             bigint NOT NULL REFERENCES ingestion_run,
  ADD COLUMN metadata_payload_id bigint REFERENCES raw_payload,
  ADD COLUMN download_payload_id bigint NOT NULL REFERENCES raw_payload,
  ADD COLUMN provider_short     text NOT NULL,
  ADD COLUMN title              text,
  ADD COLUMN instrument         text,
  ADD COLUMN rights_text        text NOT NULL,
  ADD COLUMN terms_url          text,
  ADD COLUMN release_date       date,
  ADD COLUMN center_ra_deg      double precision,
  ADD COLUMN center_dec_deg     double precision,
  ADD COLUMN fov_deg            double precision,
  ADD COLUMN fov_basis          text,
  ADD COLUMN coverage_ratio     double precision,
  ADD COLUMN separation_arcmin  double precision,
  ADD COLUMN derived_from_measurement_id bigint REFERENCES measurement ON DELETE SET NULL,
  ADD COLUMN match_basis        text NOT NULL,
  ADD COLUMN curated_hero       boolean NOT NULL DEFAULT false,
  ADD COLUMN storage_key        text NOT NULL,
  ADD COLUMN content_sha256     text NOT NULL,
  ADD COLUMN width_px           int NOT NULL,
  ADD COLUMN height_px          int NOT NULL,
  ADD COLUMN byte_size          int NOT NULL,
  ADD COLUMN thumb_storage_key  text NOT NULL,
  ADD COLUMN thumb_sha256       text NOT NULL,
  ADD COLUMN thumb_width_px     int NOT NULL,
  ADD COLUMN thumb_height_px    int NOT NULL,
  ADD COLUMN gallery_rank       smallint NOT NULL DEFAULT 0,
  ADD COLUMN updated_at         timestamptz NOT NULL DEFAULT now(),
  ADD CONSTRAINT object_image_license_fk FOREIGN KEY (license_code) REFERENCES image_license (code),
  ADD CONSTRAINT object_image_identity UNIQUE (object_id, source_id, source_image_id),
  -- ilustracao/diagrama nunca e hero: garantido pelo banco, nao so pelo codigo
  ADD CONSTRAINT object_image_hero_kind CHECK (NOT is_hero OR kind IN ('observation','survey_cutout','processed_composite')),
  ADD CONSTRAINT object_image_curated_hero_kind CHECK (NOT curated_hero OR kind IN ('observation','survey_cutout','processed_composite')),
  ADD CONSTRAINT object_image_sha CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT object_image_thumb_sha CHECK (thumb_sha256 ~ '^[0-9a-f]{64}$');

CREATE UNIQUE INDEX one_hero_image ON object_image (object_id) WHERE is_hero;
CREATE INDEX object_image_content_sha ON object_image (content_sha256);
CREATE INDEX object_image_thumb_sha ON object_image (thumb_sha256);

COMMENT ON COLUMN object_image.image_url IS 'URL exata de onde os bytes foram baixados (proveniencia), nao a URL servida';
COMMENT ON COLUMN object_image.thumb_url IS 'Sempre NULL: a miniatura e derivada localmente (thumb_storage_key), nao vem da fonte';
COMMENT ON COLUMN object_image.page_url IS 'ESA: AVM ReferenceURL; recorte: a propria URL do hips2fits (refaz o recorte)';
COMMENT ON COLUMN object_image.provider IS '"ESA/Hubble", "ESA/Webb", "CDS hips2fits (<obs_title do survey>)"';
COMMENT ON COLUMN object_image.provider_short IS 'Rotulo curto para miniaturas: "ESA/Hubble", "ESA/Webb", "DSS2 via CDS", "PanSTARRS via CDS"';
COMMENT ON COLUMN object_image.credit_verbatim IS 'AVM Credit / HiPS obs_copyright, literal';
COMMENT ON COLUMN object_image.mission IS 'AVM Facility (unidas por ", ") / HiPS obs_title';
COMMENT ON COLUMN object_image.wavelength IS 'AVM Spectral.Band (distintas, unidas por ", "); NULL se ausente — nunca presumido';
COMMENT ON COLUMN object_image.rights_text IS 'ESA: AVM Rights literal; recorte: "HiPS © <hips_copyright> — <hips_license>" do properties';
COMMENT ON COLUMN object_image.license_url IS 'Copia de image_license.url no momento da gravacao';
COMMENT ON COLUMN object_image.release_date IS 'AVM Date: data de PUBLICACAO da imagem, nao de observacao';
COMMENT ON COLUMN object_image.fov_deg IS 'Maior lado do quadro, em graus (hips2fits: fov pedido; ESA: |Spatial.Scale| x ReferenceDimension)';
COMMENT ON COLUMN object_image.coverage_ratio IS 'ESA: menor lado do quadro / ang_size_major do objeto; NULL se algum dos dois for desconhecido';
COMMENT ON COLUMN object_image.separation_arcmin IS 'ESA: distancia entre Spatial.ReferenceValue e a posicao preferida do objeto';
COMMENT ON COLUMN object_image.curated_hero IS 'Curadoria manual (images/curation.csv, action=hero) escolheu esta imagem como hero';
