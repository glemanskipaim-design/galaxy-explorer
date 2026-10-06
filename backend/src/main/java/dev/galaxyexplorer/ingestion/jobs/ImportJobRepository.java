package dev.galaxyexplorer.ingestion.jobs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.galaxyexplorer.catalog.JdbcValues;

@Repository
public class ImportJobRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public ImportJobRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @throws org.springframework.dao.DuplicateKeyException se ja existe job ativo
     *         para o mesmo termo (indice unico parcial {@code one_active_import})
     */
    public ImportJob insertQueued(String query, String normalized) {
        return jdbc.sql("""
                INSERT INTO import_job (query, normalized_query, status, created_at)
                VALUES (:query, :normalized, 'queued', :now)
                RETURNING *
                """)
                .param("query", query)
                .param("normalized", normalized)
                .param("now", OffsetDateTime.now(clock))
                .query((rs, n) -> map(rs))
                .single();
    }

    public Optional<ImportJob> findActiveByNormalized(String normalized) {
        return jdbc.sql("""
                SELECT * FROM import_job
                WHERE normalized_query = :normalized AND status IN ('queued', 'running')
                """)
                .param("normalized", normalized)
                .query((rs, n) -> map(rs))
                .optional();
    }

    public Optional<ImportJob> findById(long id) {
        return jdbc.sql("SELECT * FROM import_job WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> map(rs))
                .optional();
    }

    public void markRunning(long id) {
        jdbc.sql("UPDATE import_job SET status = 'running', started_at = :now WHERE id = :id")
                .param("id", id)
                .param("now", OffsetDateTime.now(clock))
                .update();
    }

    /**
     * @param message pode ser {@code null}
     * @param runId   pode ser {@code null}
     */
    public void markFinished(long id, ImportJobStatus status, String message, Long runId, List<UUID> objectIds) {
        if (status.active()) {
            throw new IllegalArgumentException("status final esperado, veio " + status);
        }
        jdbc.sql("""
                UPDATE import_job
                SET status = :status, message = :message, run_id = :runId, result_object_ids = :ids,
                    finished_at = :now
                WHERE id = :id
                """)
                .param("id", id)
                .param("status", status.dbValue())
                .param("message", message)
                .param("runId", runId)
                .param("ids", objectIds.toArray(new UUID[0]))
                .param("now", OffsetDateTime.now(clock))
                .update();
    }

    public int deleteById(long id) {
        return jdbc.sql("DELETE FROM import_job WHERE id = :id").param("id", id).update();
    }

    /** Retencao: apaga jobs TERMINAIS criados antes de {@code cutoff}; ativos nunca. */
    public int deleteFinishedBefore(Instant cutoff) {
        return jdbc.sql("""
                DELETE FROM import_job
                WHERE status NOT IN ('queued', 'running') AND created_at < :cutoff
                """)
                .param("cutoff", JdbcValues.timestamptz(cutoff))
                .update();
    }

    /** Jobs deixados ativos por um restart viram {@code failed} — nunca ficam presos em RUNNING. */
    public int failInterrupted(String message) {
        return jdbc.sql("""
                UPDATE import_job SET status = 'failed', message = :message, finished_at = :now
                WHERE status IN ('queued', 'running')
                """)
                .param("message", message)
                .param("now", OffsetDateTime.now(clock))
                .update();
    }

    private static ImportJob map(ResultSet rs) throws SQLException {
        return new ImportJob(rs.getLong("id"), rs.getString("query"), rs.getString("normalized_query"),
                ImportJobStatus.fromDb(rs.getString("status")), rs.getString("message"),
                JdbcValues.nullableLong(rs, "run_id"), JdbcValues.uuidList(rs, "result_object_ids"),
                JdbcValues.instant(rs, "created_at"), JdbcValues.instant(rs, "started_at"),
                JdbcValues.instant(rs, "finished_at"));
    }
}
