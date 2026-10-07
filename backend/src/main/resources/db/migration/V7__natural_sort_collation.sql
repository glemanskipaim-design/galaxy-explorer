-- Ordenacao "natural" de nomes: M 2 antes de M 10 antes de M 100.
-- Collation ICU com ordenacao numerica (kn = numeric ordering). A imagem
-- oficial postgres:17 (compose e Testcontainers) e compilada com ICU.
-- Uso: ORDER BY display_name COLLATE natural_sort, slug
CREATE COLLATION IF NOT EXISTS natural_sort (provider = icu, locale = 'und-u-kn-true');
