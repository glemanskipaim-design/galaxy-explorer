package dev.galaxyexplorer.ingestion.connector;

import java.util.List;

/** Uma ou mais consultas do usuario que resolveram para o mesmo objeto da fonte. */
public record ResolvedName(List<String> queries, SourceObjectRef ref) {

    public ResolvedName {
        queries = List.copyOf(queries);
    }
}
