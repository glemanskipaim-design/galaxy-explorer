package dev.galaxyexplorer.ingestion.pipeline;

import java.util.Locale;

/** Espelha o CHECK de {@code ingestion_run.trigger}. */
public enum RunTrigger {
    SEED,
    SCHEDULED,
    ON_DEMAND,
    MANUAL;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
