package dev.galaxyexplorer.ingestion.images;

import java.util.List;

import dev.galaxyexplorer.ingestion.pipeline.RunStatus;

/** Um run de imagens fechado: id, status final, desfecho por objeto e avisos. */
public record ImageRunResult(long runId, RunStatus status, List<ImageOutcome> outcomes, List<String> issues) {

    public ImageRunResult {
        outcomes = List.copyOf(outcomes);
        issues = List.copyOf(issues);
    }
}
