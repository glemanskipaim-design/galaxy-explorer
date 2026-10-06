package dev.galaxyexplorer.ingestion.connector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Respostas cruas de um lote, uma por dataset (consulta). */
public record FetchedBatch(List<ResolvedName> resolved, Map<String, RawPayload> payloadsByDataset) {

    public FetchedBatch {
        resolved = List.copyOf(resolved);
        payloadsByDataset = Collections.unmodifiableMap(new LinkedHashMap<>(payloadsByDataset));
    }
}
