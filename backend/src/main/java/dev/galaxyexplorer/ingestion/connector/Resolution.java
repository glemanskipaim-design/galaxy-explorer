package dev.galaxyexplorer.ingestion.connector;

import java.util.List;

/** @param raw resposta crua da resolucao (para {@code raw_payload}) */
public record Resolution(List<ResolvedName> resolved, List<UnresolvedQuery> unresolved, RawPayload raw) {

    public Resolution {
        resolved = List.copyOf(resolved);
        unresolved = List.copyOf(unresolved);
    }
}
