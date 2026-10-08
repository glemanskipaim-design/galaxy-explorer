package dev.galaxyexplorer.ingestion.pipeline;

import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.galaxyexplorer.catalog.JdbcValues;
import dev.galaxyexplorer.ingestion.connector.RawPayload;

/**
 * Acesso a {@code raw_payload}. {@code body} e {@code jsonb NOT NULL}:
 * resposta JSON (TAP) vira documento jsonb; resposta em texto (XML do Sesame)
 * vira uma string jsonb com o texto exato. {@code content_sha256} e sempre
 * calculado sobre o texto original recebido.
 */
@Repository
public class RawPayloadRepository {

    private final JdbcClient jdbc;

    public RawPayloadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Retencao: apaga os payloads crus de runs SOB DEMANDA que terminaram sem gravar
     * nenhum objeto ({@code records_ok = 0}: nome inexistente, fonte fora...) antes de
     * {@code cutoff}. Runs que gravaram objetos nunca sao tocados — seus payloads sao a
     * proveniencia ({@code measurement.raw_payload_id}) do que esta no catalogo. O
     * registro do run em si fica (e pequeno e documenta a tentativa).
     *
     * <p>{@code records_ok = 0} nao basta: um run interrompido (fechado por
     * {@code failDangling}) fica com 0 mas pode ter gravado objetos. Por isso o
     * {@code NOT EXISTS}: payload referenciado nunca e apagado. FKs para {@code raw_payload}
     * (conferidas em {@code pg_constraint} apos a V8): {@code measurement.raw_payload_id},
     * {@code object_image.metadata_payload_id}, {@code object_image.download_payload_id} e
     * {@code hips_survey_snapshot.raw_payload_id}. Nova FK para esta tabela tem de entrar aqui
     * (ha teste que compara esta lista com {@code pg_constraint}).
     */
    public int deleteForUnproductiveOnDemandRunsBefore(Instant cutoff) {
        return jdbc.sql("""
                DELETE FROM raw_payload p
                USING ingestion_run r
                WHERE p.run_id = r.id AND r.trigger = 'on_demand' AND r.records_ok = 0
                  AND r.status <> 'running' AND r.finished_at < :cutoff
                  AND NOT EXISTS (SELECT 1 FROM measurement m WHERE m.raw_payload_id = p.id)
                  AND NOT EXISTS (SELECT 1 FROM object_image i
                                  WHERE i.metadata_payload_id = p.id OR i.download_payload_id = p.id)
                  AND NOT EXISTS (SELECT 1 FROM hips_survey_snapshot h WHERE h.raw_payload_id = p.id)
                """)
                .param("cutoff", JdbcValues.timestamptz(cutoff))
                .update();
    }

    public long insert(long runId, RawPayload p) {
        String bodyExpr = p.format() == RawPayload.BodyFormat.JSON ? "CAST(:body AS jsonb)"
                : "to_jsonb(CAST(:body AS text))";
        return jdbc.sql("""
                INSERT INTO raw_payload (run_id, request_url, query_text, dataset, content_sha256, body, retrieved_at)
                VALUES (:runId, :url, :query, :dataset, :sha, %s, :retrievedAt)
                RETURNING id
                """.formatted(bodyExpr))
                .param("runId", runId)
                .param("url", p.requestUrl())
                .param("query", p.queryText())
                .param("dataset", p.dataset())
                .param("sha", p.sha256())
                .param("body", p.body())
                .param("retrievedAt", JdbcValues.timestamptz(p.retrievedAt()))
                .query(Long.class)
                .single();
    }
}
