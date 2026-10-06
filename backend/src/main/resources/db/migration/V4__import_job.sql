-- V4: pedidos de importacao sob demanda (POST /api/v1/imports).
--
-- ingestion_run registra uma execucao contra uma fonte; nao guarda "o termo
-- que o usuario pediu" nem o resultado do pedido. Em memoria o status se
-- perderia a cada restart (e o frontend faz polling), por isso tabela propria.

CREATE TABLE import_job (
  id                bigserial PRIMARY KEY,
  query             text NOT NULL,             -- como o usuario digitou (com trim)
  normalized_query  text NOT NULL,             -- IdentifierNormalizer
  status            text NOT NULL CHECK (status IN ('queued','running','succeeded','not_found','failed')),
  message           text,
  run_id            bigint REFERENCES ingestion_run,
  result_object_ids uuid[] NOT NULL DEFAULT '{}',
  created_at        timestamptz NOT NULL DEFAULT now(),
  started_at        timestamptz,
  finished_at       timestamptz
);

-- O banco garante: o mesmo termo nao dispara dois jobs ativos ao mesmo tempo.
CREATE UNIQUE INDEX one_active_import ON import_job (normalized_query)
  WHERE status IN ('queued','running');
