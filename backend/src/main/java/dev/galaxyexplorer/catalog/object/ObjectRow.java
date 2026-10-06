package dev.galaxyexplorer.catalog.object;

import java.time.Instant;
import java.util.UUID;

/**
 * @param categoryId    pode ser {@code null}
 * @param categoryBasis pode ser {@code null}
 */
public record ObjectRow(UUID id, String slug, String displayName, Integer categoryId, String categoryBasis,
                        String existenceStatus, Instant createdAt, Instant updatedAt) {
}
