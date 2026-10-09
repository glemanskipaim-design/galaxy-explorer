package dev.galaxyexplorer.ingestion.pipeline;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.galaxyexplorer.catalog.SourceCode;

/**
 * Acesso a {@code ingestion_run}. Chamado fora de transacao (autocommit): o
 * run precisa existir em disco mesmo que o resto do processamento exploda.
 */
@Repository
public class IngestionRunRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public IngestionRunRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public long open(short sourceId, RunTrigger trigger, String connectorVersion) {
        return jdbc.sql("""
                INSERT INTO ingestion_run (source_id, trigger, started_at, status, connector_version)
                VALUES (:sourceId, :trigger, :now, 'running', :version)
                RETURNING id
                """)
                .param("sourceId", sourceId)
                .param("trigger", trigger.dbValue())
                .param("now", OffsetDateTime.now(clock))
                .param("version", connectorVersion)
                .query(Long.class)
                .single();
    }

    /** @param errorSummary pode ser {@code null} */
    public void close(long runId, RunStatus status, int ok, int failed, String errorSummary) {
        jdbc.sql("""
                UPDATE ingestion_run
                SET status = :status, finished_at = :now, records_ok = :ok, records_failed = :failed,
                    error_summary = :summary
                WHERE id = :id
                """)
                .param("id", runId)
                .param("status", status.dbValue())
                .param("now", OffsetDateTime.now(clock))
                .param("ok", ok)
                .param("failed", failed)
                .param("summary", errorSummary)
                .update();
    }

    /** Runs deixados em {@code running} por um crash/restart viram {@code failed}. */
    public int failDangling(String reason) {
        return jdbc.sql("""
                UPDATE ingestion_run SET status = 'failed', finished_at = :now, error_summary = :reason
                WHERE status = 'running'
                """)
                .param("now", OffsetDateTime.now(clock))
                .param("reason", reason)
                .update();
    }

    /** Status do run mais recente da fonte com esse gatilho; vazio se nunca rodou. */
    public Optional<RunStatus> latestStatus(SourceCode source, RunTrigger trigger) {
        return jdbc.sql("""
                SELECT r.status
                FROM ingestion_run r
                JOIN source s ON s.id = r.source_id
                WHERE s.code = :code AND r.trigger = :trigger
                ORDER BY r.started_at DESC, r.id DESC
                LIMIT 1
                """)
                .param("code", source.name())
                .param("trigger", trigger.dbValue())
                .query(String.class)
                .optional()
                .map(v -> RunStatus.valueOf(v.toUpperCase(Locale.ROOT)));
    }
}
