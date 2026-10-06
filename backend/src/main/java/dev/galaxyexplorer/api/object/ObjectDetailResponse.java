package dev.galaxyexplorer.api.object;

import java.time.Instant;
import java.util.List;

/**
 * Contrato JSON do detalhe de um objeto. Separado do read model de proposito:
 * o read model pode mudar sem quebrar o frontend.
 */
public record ObjectDetailResponse(String slug, String displayName, Category category, String existenceStatus,
                                   List<SourceType> sourceTypes, List<Identifier> identifiers, Position position,
                                   List<Property> properties, List<Source> sources) {

    /** @param basis raciocinio da classificacao (de onde veio a categoria) */
    public record Category(String code, String namePt, String nameEn, String basis) {
    }

    /** @param sourceRef id do objeto na fonte (oid do SIMBAD) */
    public record SourceType(String source, String sourceRef, String sourceType, String morphType) {
    }

    public record Identifier(String identifier, String catalog, boolean isCommonName, String source) {
    }

    public record Position(double raDeg, double decDeg, String frame, String epoch, String source,
                           Reference reference, Instant retrievedAt) {
    }

    public record Reference(String bibcode, String adsUrl) {
    }

    /** @param snr S/N da paralaxe quando o motivo envolve S/N; senao {@code null} */
    public record Absence(String code, String message, Double snr) {
    }

    public record Property(String code, String label, String group, String qualifier, boolean available,
                           Absence absence, Measurement preferred, List<Measurement> alternatives) {
    }

    public record Measurement(long id, Double value, Double errMinus, Double errPlus, String limitFlag,
                              String valueText, String unit, String originalValue, String originalUnit, String originalError,
                              String epistemic, String method, List<Long> derivedFrom, String source, String dataset,
                              String sourceUrl, Reference reference, Instant retrievedAt) {
    }

    public record Source(String code, String name, String homepageUrl, String termsUrl, String acknowledgment) {
    }
}
