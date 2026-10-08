package dev.galaxyexplorer.ingestion.connector.image;

import java.util.List;
import java.util.UUID;

/**
 * Objeto do catalogo para o qual se buscam imagens (modelo canonico de entrada).
 *
 * @param identifiers          como gravados ({@code 'M  51'}, {@code 'NAME Whirlpool Galaxy'})
 * @param raDeg                posicao preferida; {@code null} = objeto sem posicao
 * @param decDeg               idem
 * @param positionSource       fonte da posicao preferida ({@code SIMBAD}); {@code null} sem posicao
 * @param angSizeMajorArcmin   tamanho angular preferido (eixo maior); pode ser {@code null}
 * @param angSizeMeasurementId id da medicao usada; pode ser {@code null}
 * @param angSizeSource        fonte da medicao ({@code SIMBAD}); pode ser {@code null}
 */
public record ImageTarget(UUID objectId, String slug, String displayName, List<String> identifiers, Double raDeg,
                          Double decDeg, String positionSource, Double angSizeMajorArcmin,
                          Long angSizeMeasurementId, String angSizeSource) {

    public ImageTarget {
        identifiers = List.copyOf(identifiers);
    }

    public boolean hasPosition() {
        return raDeg != null && decDeg != null;
    }
}
