package dev.galaxyexplorer.api.imports;

import java.time.Instant;
import java.util.List;

/**
 * @param status     {@code queued | running | succeeded | not_found | failed}
 * @param message    pode ser {@code null}
 * @param runId      pode ser {@code null}
 * @param startedAt  pode ser {@code null}
 * @param finishedAt pode ser {@code null}
 */
public record ImportJobResponse(long id, String query, String status, String message, List<String> resultSlugs,
                                Long runId, Instant createdAt, Instant startedAt, Instant finishedAt) {
}
