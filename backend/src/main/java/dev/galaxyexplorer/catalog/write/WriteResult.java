package dev.galaxyexplorer.catalog.write;

import java.util.UUID;

public record WriteResult(UUID objectId, String slug, boolean created, int measurementsUpserted,
                          int measurementsDeleted) {
}
