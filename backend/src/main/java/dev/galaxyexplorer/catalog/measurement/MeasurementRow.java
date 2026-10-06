package dev.galaxyexplorer.catalog.measurement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Uma linha de {@code measurement} + codigo da fonte + bibcode (via join).
 * Campos numericos/texto opcionais podem ser {@code null}.
 */
public record MeasurementRow(long id, UUID objectId, String propertyCode, String qualifier, Double valueNum,
                             Double errMinus, Double errPlus, String limitFlag, String valueText,
                             String originalValue, String originalUnit, String originalError, String epistemic, String method,
                             List<Long> derivedFrom, short sourceId, String sourceCode, String dataset,
                             String sourceUrl, Long referenceId, String bibcode, Long rawPayloadId,
                             Instant retrievedAt, boolean preferred) {
}
