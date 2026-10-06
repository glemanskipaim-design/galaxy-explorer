package dev.galaxyexplorer.catalog;

import java.util.Locale;

/**
 * Natureza epistemica de um valor: observado, derivado pela fonte, calculado
 * por nos, estimado ou de modelo. Espelha o CHECK de {@code measurement.epistemic}.
 */
public enum Epistemic {
    OBSERVED,
    DERIVED_BY_SOURCE,
    COMPUTED_BY_US,
    ESTIMATED,
    MODEL;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Epistemic fromDb(String value) {
        if (value == null) {
            throw new IllegalArgumentException("epistemic nulo");
        }
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
