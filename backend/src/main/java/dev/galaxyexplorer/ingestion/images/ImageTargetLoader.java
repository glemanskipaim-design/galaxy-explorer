package dev.galaxyexplorer.ingestion.images;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.galaxyexplorer.catalog.identifier.IdentifierRepository;
import dev.galaxyexplorer.catalog.identifier.IdentifierRow;
import dev.galaxyexplorer.catalog.measurement.MeasurementRepository;
import dev.galaxyexplorer.catalog.measurement.MeasurementRow;
import dev.galaxyexplorer.catalog.object.CelestialObjectRepository;
import dev.galaxyexplorer.catalog.object.ObjectRow;
import dev.galaxyexplorer.catalog.position.PositionRepository;
import dev.galaxyexplorer.catalog.position.PositionRow;
import dev.galaxyexplorer.ingestion.connector.image.ImageTarget;

/**
 * Monta {@link ImageTarget} a partir do catalogo: posicao preferida, tamanho
 * angular preferido ({@code ang_size_major}) e identificadores.
 */
@Component
public class ImageTargetLoader {

    static final String ANG_SIZE_MAJOR = "ang_size_major";

    private final CelestialObjectRepository objects;
    private final IdentifierRepository identifiers;
    private final PositionRepository positions;
    private final MeasurementRepository measurements;

    public ImageTargetLoader(CelestialObjectRepository objects, IdentifierRepository identifiers,
                             PositionRepository positions, MeasurementRepository measurements) {
        this.objects = objects;
        this.identifiers = identifiers;
        this.positions = positions;
        this.measurements = measurements;
    }

    /** @param ids vazio = todos os objetos */
    public List<ImageTarget> load(List<UUID> ids) {
        List<ObjectRow> rows = ids.isEmpty() ? objects.findAll()
                : objects.findByIds(ids).stream().sorted(Comparator.comparing(ObjectRow::slug)).toList();
        return rows.stream().map(this::toTarget).toList();
    }

    private ImageTarget toTarget(ObjectRow o) {
        List<String> ids = identifiers.findByObject(o.id()).stream().map(IdentifierRow::identifier).toList();
        Optional<PositionRow> position = positions.findPreferred(o.id());
        Optional<MeasurementRow> size = measurements.findByObjectAndProperty(o.id(), ANG_SIZE_MAJOR).stream()
                .filter(m -> m.valueNum() != null && m.valueNum() > 0)
                .min(Comparator.comparing((MeasurementRow m) -> !m.preferred()).thenComparingLong(MeasurementRow::id));
        return new ImageTarget(o.id(), o.slug(), o.displayName(), ids, position.map(PositionRow::raDeg).orElse(null),
                position.map(PositionRow::decDeg).orElse(null), position.map(PositionRow::sourceCode).orElse(null),
                size.map(MeasurementRow::valueNum).orElse(null), size.map(MeasurementRow::id).orElse(null),
                size.map(MeasurementRow::sourceCode).orElse(null));
    }
}
