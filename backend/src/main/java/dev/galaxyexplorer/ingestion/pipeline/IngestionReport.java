package dev.galaxyexplorer.ingestion.pipeline;

import java.util.List;

/** @param issues avisos por valor (nao derrubaram objetos) + problemas de esquema */
public record IngestionReport(long runId, RunStatus status, int recordsOk, int recordsFailed,
                              List<ObjectOutcome> outcomes, List<String> issues) {

    public IngestionReport {
        outcomes = List.copyOf(outcomes);
        issues = List.copyOf(issues);
    }
}
