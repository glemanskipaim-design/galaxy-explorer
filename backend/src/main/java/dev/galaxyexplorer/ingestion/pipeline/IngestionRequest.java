package dev.galaxyexplorer.ingestion.pipeline;

import java.util.List;
import java.util.Objects;

import dev.galaxyexplorer.catalog.SourceCode;

/** @param queries nomes como o usuario/seed escreveu; nao vazia, no maximo {@value #MAX_QUERIES} */
public record IngestionRequest(SourceCode source, RunTrigger trigger, List<String> queries) {

    public static final int MAX_QUERIES = 500;

    public IngestionRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(trigger, "trigger");
        if (queries == null || queries.isEmpty()) {
            throw new IllegalArgumentException("pedido de ingestao sem consultas");
        }
        if (queries.size() > MAX_QUERIES) {
            throw new IllegalArgumentException("pedido de ingestao com " + queries.size() + " consultas (max "
                    + MAX_QUERIES + ")");
        }
        queries = List.copyOf(queries);
    }
}
