package dev.galaxyexplorer.ingestion.jobs;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido de importacao sob demanda.
 *
 * @param message    pode ser {@code null}
 * @param runId      {@code null} se nem chegou a rodar a ingestao
 * @param startedAt  pode ser {@code null}
 * @param finishedAt pode ser {@code null}
 */
public record ImportJob(long id, String query, String normalizedQuery, ImportJobStatus status, String message,
                        Long runId, List<UUID> resultObjectIds, Instant createdAt, Instant startedAt,
                        Instant finishedAt) {

    public ImportJob {
        resultObjectIds = List.copyOf(resultObjectIds);
    }
}
