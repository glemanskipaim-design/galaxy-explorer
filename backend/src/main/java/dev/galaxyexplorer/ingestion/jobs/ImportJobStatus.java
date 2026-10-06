package dev.galaxyexplorer.ingestion.jobs;

import java.util.Locale;

/** Espelha o CHECK de {@code import_job.status}. */
public enum ImportJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    NOT_FOUND,
    FAILED;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ImportJobStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    public boolean active() {
        return this == QUEUED || this == RUNNING;
    }
}
