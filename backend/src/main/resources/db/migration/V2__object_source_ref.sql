-- V2: identidade do objeto na fonte (decisao do usuario, adendo do plano do Bloco 1).
--
-- O esquema da secao 6 nao tinha onde guardar o id do objeto na fonte. Sem ele,
-- reconhecer "o mesmo objeto" entre execucoes exigiria casar identificadores
-- (heuristica indireta). Com source_ref, a identidade entre execucoes e
-- exatamente (source_id, source_ref):
--   SIMBAD   -> oid (basic.oid, o mesmo devolvido pelo Sesame), como texto
--   futuras  -> pl_name (Exoplanet Archive), spkid (JPL) etc.
--
-- Migracao separada para que V1 continue sendo copia literal da secao 6.

ALTER TABLE object_source_type ADD COLUMN source_ref text NOT NULL;

ALTER TABLE object_source_type
  ADD CONSTRAINT object_source_type_source_ref_key UNIQUE (source_id, source_ref);
