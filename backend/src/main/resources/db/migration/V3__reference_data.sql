-- V3: dados de referencia.
--
-- Regras:
--  * ids nunca fixados; tudo referenciado por code via subselect.
--  * todo source_type SIMBAD daqui existe no otypedef real
--    (fixture src/test/resources/fixtures/simbad/otypedef_all.json; ha teste).
--  * URLs verificadas ao vivo em 2026-10-05; textos de agradecimento copiados
--    literalmente das paginas oficiais.

-- ── Fontes ───────────────────────────────────────────────────────────────
-- SIMBAD: texto do quadro "Acknowledgment" de https://simbad.cds.unistra.fr/simbad/
-- (frase + citacao, cada uma como aparece na pagina, separadas por quebra de linha).
-- terms_url: pagina "legals" do CDS, linkada na home do SIMBAD.
-- NASA_EXO: texto de https://exoplanetarchive.ipac.caltech.edu/docs/acknowledge.html
INSERT INTO source (code, name, homepage_url, terms_url, acknowledgment) VALUES
  ('SIMBAD',
   'SIMBAD Astronomical Database (CDS, Strasbourg)',
   'https://simbad.cds.unistra.fr/simbad/',
   'https://cds.unistra.fr/legals/',
   E'This research has made use of the SIMBAD database, operated at CDS, Strasbourg, France\n2000,A&AS,143,9, "The SIMBAD astronomical database", Wenger et al.'),
  ('NASA_EXO',
   'NASA Exoplanet Archive',
   'https://exoplanetarchive.ipac.caltech.edu/',
   'https://exoplanetarchive.ipac.caltech.edu/docs/acknowledge.html',
   'This research has made use of the NASA Exoplanet Archive, which is operated by the California Institute of Technology, under contract with the National Aeronautics and Space Administration under the Exoplanet Exploration Program.');

-- ── Categorias (hierarquia) ─────────────────────────────────────────────
INSERT INTO object_category (code, parent_id, name_pt, name_en) VALUES
  ('star',         NULL, 'Estrela',                      'Star'),
  ('galaxy',       NULL, 'Galáxia',                      'Galaxy'),
  ('nebula',       NULL, 'Nebulosa',                     'Nebula'),
  ('cluster',      NULL, 'Aglomerado estelar',           'Star cluster'),
  ('agn',          NULL, 'Núcleo galáctico ativo (AGN)', 'Active galactic nucleus'),
  ('black_hole',   NULL, 'Buraco negro',                 'Black hole'),
  ('exoplanet',    NULL, 'Exoplaneta',                   'Exoplanet'),
  ('x_ray_source', NULL, 'Fonte de raios X',             'X-ray source'),
  ('other',        NULL, 'Outro',                        'Other');

INSERT INTO object_category (code, parent_id, name_pt, name_en)
SELECT c.code, p.id, c.name_pt, c.name_en
FROM (VALUES
  ('galaxy_spiral',      'galaxy',  'Galáxia espiral',           'Spiral galaxy'),
  ('galaxy_elliptical',  'galaxy',  'Galáxia elíptica',          'Elliptical galaxy'),
  ('galaxy_lenticular',  'galaxy',  'Galáxia lenticular',        'Lenticular galaxy'),
  ('galaxy_irregular',   'galaxy',  'Galáxia irregular',         'Irregular galaxy'),
  ('nebula_planetary',   'nebula',  'Nebulosa planetária',       'Planetary nebula'),
  ('nebula_emission',    'nebula',  'Nebulosa de emissão',       'Emission nebula'),
  ('nebula_reflection',  'nebula',  'Nebulosa de reflexão',      'Reflection nebula'),
  ('nebula_snr',         'nebula',  'Remanescente de supernova', 'Supernova remnant'),
  ('cluster_open',       'cluster', 'Aglomerado aberto',         'Open cluster'),
  ('cluster_globular',   'cluster', 'Aglomerado globular',       'Globular cluster'),
  ('quasar',             'agn',     'Quasar',                    'Quasar')
) AS c(code, parent_code, name_pt, name_en)
JOIN object_category p ON p.code = c.parent_code;

-- ── Propriedades ────────────────────────────────────────────────────────
INSERT INTO property_definition (code, label_pt, canonical_unit, value_kind, display_group) VALUES
  ('distance',        'Distância',                    'pc',     'numeric', 'astrometry'),
  ('parallax',        'Paralaxe',                     'mas',    'numeric', 'astrometry'),
  ('redshift',        'Redshift (z)',                 NULL,     'numeric', 'kinematics'),
  ('radial_velocity', 'Velocidade radial',            'km/s',   'numeric', 'kinematics'),
  ('app_mag',         'Magnitude aparente',           'mag',    'numeric', 'photometry'),
  ('spectral_type',   'Tipo espectral',               NULL,     'text',    'classification'),
  ('morph_type',      'Tipo morfológico',             NULL,     'text',    'classification'),
  ('ang_size_major',  'Tamanho angular (eixo maior)', 'arcmin', 'numeric', 'morphology'),
  ('ang_size_minor',  'Tamanho angular (eixo menor)', 'arcmin', 'numeric', 'morphology');

-- ── Mapeamento otype SIMBAD -> categoria ────────────────────────────────
-- Regex: dialeto Java, Pattern.find() sobre morph_type.trim(); '' = qualquer.
-- Maior priority vence; empate -> menor id.
--
-- Otypes de galaxia/AGN: os que aparecem de fato nos 110 Messier
-- (fixture messier_types.json: G, GiG, GiP, GiC, SBG, H2G; AGN, Sy2, SyG, LIN)
-- mais os previstos no plano que existem no otypedef (BiC, rG, Sy1).
-- Cada um recebe as regras de morfologia + uma regra generica.
INSERT INTO source_type_mapping (source_id, source_type, morph_pattern, category_id, priority)
SELECT s.id, o.otype, r.pattern, c.id, r.priority
FROM (VALUES
  ('G'), ('GiG'), ('GiP'), ('GiC'), ('BiC'), ('SBG'), ('H2G'), ('rG'),
  ('AGN'), ('Sy1'), ('Sy2'), ('SyG'), ('LIN')
) AS o(otype)
CROSS JOIN (VALUES
  -- lenticular: S0, SA0, SB(s)0, (R)SB0...
  ('^(\(R''?\))?S(A|B|AB)?(\([^)]*\))?0',      'galaxy_lenticular', 30),
  -- espiral com estagio: Sb, SA(s)b, SAB(rs)cd, SBa, Sc(dSc)...
  ('^(\(R''?\))?S(A|B|AB)?(\([^)]*\))?[abcdm]', 'galaxy_spiral',     20),
  -- eliptica: E2, cE2, dE5, E5pec, E-E/S0
  ('^[cd]?E',                                  'galaxy_elliptical', 20),
  -- irregular: Irr, IBm, I0, Im
  ('^d?I',                                     'galaxy_irregular',  20),
  -- [DESVIO DO PLANO, ver relatorio do Bloco 1] espiral sem estagio.
  -- O dado real do SIMBAD tem varias morfologias sem letra de estagio:
  -- S (M82, M102), SA (M51, M81), SAB (M98, M106), SB (M109) e grafias com
  -- sublinhado S_AB (M90, M94, M101), SA_B (M58, M66), SA_B_x (M65), SB_a (M91).
  -- Todas comecam com 'S' (galaxia espiral na notacao de de Vaucouleurs).
  -- Prioridade 10: perde para lenticular (S0...) e para as regras com estagio.
  ('^(\(R''?\))?S',                            'galaxy_spiral',     10)
) AS r(pattern, category_code, priority)
JOIN source s ON s.code = 'SIMBAD'
JOIN object_category c ON c.code = r.category_code;

-- Regra generica ('' = qualquer morfologia, priority 0) e demais otypes.
-- Sgr A* (otype X) fica x_ray_source, nao black_hole: buraco negro e
-- interpretacao, nao tipo de catalogo (descoberta 2 do doc da Fase 0).
-- Otype sem regra (ex. '?' em M40, 'err' em M73) cai em 'other' em tempo de
-- execucao, com category_basis explicando — nao e erro.
INSERT INTO source_type_mapping (source_id, source_type, morph_pattern, category_id, priority)
SELECT s.id, m.otype, '', c.id, 0
FROM (VALUES
  ('G',   'galaxy'), ('GiG', 'galaxy'), ('GiP', 'galaxy'), ('GiC', 'galaxy'),
  ('BiC', 'galaxy'), ('SBG', 'galaxy'), ('H2G', 'galaxy'), ('rG',  'galaxy'),
  ('AGN', 'agn'), ('Sy1', 'agn'), ('Sy2', 'agn'), ('SyG', 'agn'), ('LIN', 'agn'),
  ('QSO', 'quasar'),
  ('GlC', 'cluster_globular'),
  ('OpC', 'cluster_open'),
  ('Cl*', 'cluster'), ('As*', 'cluster'),
  ('PN',  'nebula_planetary'),
  ('HII', 'nebula_emission'),
  ('RNe', 'nebula_reflection'),
  ('SNR', 'nebula_snr'),
  ('ISM', 'nebula'), ('Cld', 'nebula'), ('GNe', 'nebula'), ('DNe', 'nebula'), ('MoC', 'nebula'),
  ('*',   'star'), ('**',  'star'), ('s*r', 'star'),
  ('X',   'x_ray_source'),
  ('BH',  'black_hole'),
  ('Pl',  'exoplanet')
) AS m(otype, category_code)
JOIN source s ON s.code = 'SIMBAD'
JOIN object_category c ON c.code = m.category_code;

-- property_source_priority: vazio no Bloco 1 (so ha SIMBAD; as regras da
-- secao 7 envolvem NED/Gaia, fases posteriores).
