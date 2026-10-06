package dev.galaxyexplorer.ingestion.connector;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Modelo canonico que todo conector produz.
 *
 * @param mainId          identificador principal segundo o payload de DADOS ja validado
 *                        (no SIMBAD, {@code basic.main_id}); obrigatorio
 * @param morphType       pode ser {@code null}
 * @param sourceObjectUrl link clicavel para o usuario conferir o objeto na fonte
 * @param issues          avisos por valor (nao rejeitam o objeto; vao para o {@code error_summary} do run)
 */
public record NormalizedObject(SourceObjectRef ref, String mainId, List<String> queries, String sourceType,
                               String morphType, String sourceObjectUrl, List<NormalizedIdentifier> identifiers,
                               Optional<NormalizedPosition> position, List<NormalizedMeasurement> measurements,
                               List<String> issues) {

    public NormalizedObject {
        Objects.requireNonNull(ref, "ref");
        if (mainId == null || mainId.isBlank()) {
            throw new IllegalArgumentException("mainId ausente para " + ref);
        }
        queries = List.copyOf(queries);
        identifiers = List.copyOf(identifiers);
        position = position == null ? Optional.empty() : position;
        measurements = List.copyOf(measurements);
        issues = List.copyOf(issues);
    }
}
