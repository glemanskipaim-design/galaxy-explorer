package dev.galaxyexplorer.catalog.write;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.catalog.measurement.MeasurementWrite;
import dev.galaxyexplorer.catalog.position.PositionWrite;

/**
 * Tudo que uma fonte disse sobre um objeto, pronto para o catalogo. Medicoes e
 * posicao chegam com {@code bibcode} preenchido e {@code referenceId} nulo.
 *
 * @param sourceRef id do objeto na fonte (oid do SIMBAD) — identidade entre execucoes
 * @param morphType pode ser {@code null}
 */
public record ObjectWrite(SourceCode source, String sourceRef, String sourceMainId, String sourceType,
                          String morphType, List<IdentifierWrite> identifiers, Optional<PositionWrite> position,
                          List<MeasurementWrite> measurements, long runId) {

    public ObjectWrite {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceRef, "sourceRef");
        Objects.requireNonNull(sourceMainId, "sourceMainId");
        Objects.requireNonNull(sourceType, "sourceType");
        identifiers = List.copyOf(identifiers);
        position = position == null ? Optional.empty() : position;
        measurements = List.copyOf(measurements);
    }
}
