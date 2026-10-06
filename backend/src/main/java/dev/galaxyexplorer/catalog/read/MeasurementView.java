package dev.galaxyexplorer.catalog.read;

import java.time.Instant;
import java.util.List;

/** Uma medicao com proveniencia, pronta para leitura. Campos opcionais podem ser {@code null}. */
public record MeasurementView(long id, Double valueNum, Double errMinus, Double errPlus, String limitFlag,
                              String valueText, String canonicalUnit, String originalValue, String originalUnit, String originalError,
                              String epistemic, String method, List<Long> derivedFrom, String sourceCode,
                              String dataset, String sourceUrl, String bibcode, Instant retrievedAt,
                              boolean preferred) {
}
