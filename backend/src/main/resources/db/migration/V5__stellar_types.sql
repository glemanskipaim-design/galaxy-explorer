-- V5: tipos estelares do SIMBAD -> subcategorias de estrela.
--
-- Base: tabela otypedef REAL do SIMBAD (fixture src/test/resources/fixtures/simbad/
-- otypedef_all.json; reconferida ao vivo em 2026-10-06, identica: 226 otypes).
-- Todo otype abaixo existe la (ha teste). O criterio e o label/description do
-- proprio otypedef, nunca memoria:
--   * otypes "_Candidate" (terminados em '?') NAO sao mapeados: candidato a
--     tipo nao e o tipo; caem em 'other' com category_basis explicando.
--   * otype estelar sem subclasse clara no label (Star, Emission-line Star,
--     Be Star, Massive Star, Carbon Star, Evolved Star...) fica em 'star'.
-- Motivacao imediata: Vega (otype dS*, delta Sct Variable) e TRAPPIST-1
-- (otype LM*, Low-mass Star) caiam em 'other'.

-- ── Subcategorias ───────────────────────────────────────────────────────
INSERT INTO object_category (code, parent_id, name_pt, name_en)
SELECT c.code, p.id, c.name_pt, c.name_en
FROM (VALUES
  ('star_dwarf',       'star', 'Estrela anã (sequência principal)', 'Dwarf (main-sequence) star'),
  ('star_giant',       'star', 'Estrela gigante',                   'Giant star'),
  ('star_supergiant',  'star', 'Estrela supergigante',              'Supergiant star'),
  ('star_variable',    'star', 'Estrela variável',                  'Variable star'),
  ('star_binary',      'star', 'Estrela binária ou múltipla',       'Binary or multiple star'),
  ('star_young',       'star', 'Estrela jovem (YSO)',               'Young stellar object'),
  ('star_wolf_rayet',  'star', 'Estrela Wolf-Rayet',                'Wolf-Rayet star'),
  ('star_white_dwarf', 'star', 'Anã branca',                        'White dwarf'),
  ('star_neutron',     'star', 'Estrela de nêutrons',               'Neutron star')
) AS c(code, parent_code, name_pt, name_en)
JOIN object_category p ON p.code = c.parent_code;

INSERT INTO object_category (code, parent_id, name_pt, name_en)
SELECT 'star_pulsar', p.id, 'Pulsar', 'Pulsar'
FROM object_category p WHERE p.code = 'star_neutron';

-- Anã marrom e subestelar (nao funde hidrogenio): categoria raiz, nao filha de 'star'.
INSERT INTO object_category (code, parent_id, name_pt, name_en) VALUES
  ('brown_dwarf', NULL, 'Anã marrom', 'Brown dwarf');

-- ── Mapeamento (morph_pattern '' = qualquer, priority 0) ──────────────────
INSERT INTO source_type_mapping (source_id, source_type, morph_pattern, category_id, priority)
SELECT s.id, m.otype, '', c.id, 0
FROM (VALUES
  -- Main Sequence Star, Low-mass Star, Blue Straggler
  ('MS*', 'star_dwarf'), ('LM*', 'star_dwarf'), ('BS*', 'star_dwarf'),
  -- Red Giant Branch, Asymptotic Giant Branch, Horizontal Branch
  ('RG*', 'star_giant'), ('AB*', 'star_giant'), ('HB*', 'star_giant'),
  -- Red/Blue/Yellow Supergiant, Evolved Supergiant
  ('s*b', 'star_supergiant'), ('s*y', 'star_supergiant'), ('sg*', 'star_supergiant'),
  -- variaveis (label "... Variable" ou "Variable Star")
  ('V*',  'star_variable'), ('Pu*', 'star_variable'), ('Ce*', 'star_variable'), ('cC*', 'star_variable'),
  ('WV*', 'star_variable'), ('RR*', 'star_variable'), ('dS*', 'star_variable'), ('SX*', 'star_variable'),
  ('gD*', 'star_variable'), ('bC*', 'star_variable'), ('Mi*', 'star_variable'), ('LP*', 'star_variable'),
  ('RV*', 'star_variable'), ('Ir*', 'star_variable'), ('Er*', 'star_variable'), ('Or*', 'star_variable'),
  ('RC*', 'star_variable'), ('Ro*', 'star_variable'), ('BY*', 'star_variable'), ('RS*', 'star_variable'),
  ('a2*', 'star_variable'), ('El*', 'star_variable'), ('No*', 'star_variable'),
  -- binarias: Spectroscopic, Eclipsing, Cataclysmic, X-ray (geral/baixa/alta massa), Symbiotic
  ('SB*', 'star_binary'), ('EB*', 'star_binary'), ('CV*', 'star_binary'), ('XB*', 'star_binary'),
  ('LXB', 'star_binary'), ('HXB', 'star_binary'), ('Sy*', 'star_binary'),
  -- Young Stellar Object, T Tauri, Herbig Ae/Be
  ('Y*O', 'star_young'), ('TT*', 'star_young'), ('Ae*', 'star_young'),
  ('WR*', 'star_wolf_rayet'),
  ('WD*', 'star_white_dwarf'),
  ('N*',  'star_neutron'),
  ('Psr', 'star_pulsar'),
  ('BD*', 'brown_dwarf'),
  -- estelares sem subclasse no label: ficam em 'star'
  ('Em*', 'star'), ('Be*', 'star'), ('Ma*', 'star'), ('PM*', 'star'), ('HV*', 'star'), ('Pe*', 'star'),
  ('C*',  'star'), ('S*',  'star'), ('Ev*', 'star'), ('pA*', 'star'), ('OH*', 'star'), ('HS*', 'star')
) AS m(otype, category_code)
JOIN source s ON s.code = 'SIMBAD'
JOIN object_category c ON c.code = m.category_code;

-- Regras da V3 (ja aplicada; nao se edita migracao aplicada) agora mais especificas:
--   '**'  (Double or Multiple Star) -> star_binary
--   's*r' (Red Supergiant, Betelgeuse) -> star_supergiant
UPDATE source_type_mapping m
SET category_id = (SELECT id FROM object_category WHERE code = 'star_binary')
FROM source s
WHERE s.id = m.source_id AND s.code = 'SIMBAD' AND m.source_type = '**' AND m.morph_pattern = '';

UPDATE source_type_mapping m
SET category_id = (SELECT id FROM object_category WHERE code = 'star_supergiant')
FROM source s
WHERE s.id = m.source_id AND s.code = 'SIMBAD' AND m.source_type = 's*r' AND m.morph_pattern = '';
