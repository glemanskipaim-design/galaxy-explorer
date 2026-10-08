package dev.galaxyexplorer.catalog.read;

import java.util.List;
import java.util.Optional;

import dev.galaxyexplorer.catalog.category.CategoryRow;
import dev.galaxyexplorer.catalog.identifier.IdentifierRow;
import dev.galaxyexplorer.catalog.image.ObjectImageRow;
import dev.galaxyexplorer.catalog.object.ObjectRow;
import dev.galaxyexplorer.catalog.object.SourceTypeRow;
import dev.galaxyexplorer.catalog.position.PositionRow;
import dev.galaxyexplorer.catalog.source.SourceRow;

/**
 * Read model do detalhe de um objeto.
 *
 * @param category     pode ser {@code null}
 * @param images       hero primeiro, depois {@code gallery_rank}; vazia quando nao ha
 * @param imageAbsence nao-nulo se e somente se {@code images} e vazia
 */
public record ObjectDetail(ObjectRow object, CategoryRow category, List<SourceTypeRow> sourceTypes,
                           List<IdentifierRow> identifiers, Optional<PositionRow> position,
                           List<PropertyView> properties, List<SourceRow> sources, List<ObjectImageRow> images,
                           ImageAbsenceReason imageAbsence) {
}
