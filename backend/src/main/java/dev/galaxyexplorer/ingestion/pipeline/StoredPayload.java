package dev.galaxyexplorer.ingestion.pipeline;

import java.time.Instant;

/** Um {@code raw_payload} ja gravado: o id (para {@code measurement.raw_payload_id}) e quando foi coletado. */
public record StoredPayload(long id, Instant retrievedAt) {
}
