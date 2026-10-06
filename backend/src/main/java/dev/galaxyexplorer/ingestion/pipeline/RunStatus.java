package dev.galaxyexplorer.ingestion.pipeline;

import java.util.Locale;

/** Espelha o CHECK de {@code ingestion_run.status}. */
public enum RunStatus {
    RUNNING,
    OK,
    PARTIAL,
    FAILED;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
