package dev.galaxyexplorer.api.object;

import java.util.List;

import dev.galaxyexplorer.catalog.read.MeasurementView;
import dev.galaxyexplorer.catalog.read.ObjectDetail;
import dev.galaxyexplorer.catalog.read.PropertyView;
import dev.galaxyexplorer.common.Texts;

/** Read model -> contrato JSON. Puro. */
public final class ObjectDetailMapper {

    static final String ADS_ABSTRACT = "https://ui.adsabs.harvard.edu/abs/%s/abstract";

    private ObjectDetailMapper() {
    }

    public static ObjectDetailResponse from(ObjectDetail d) {
        ObjectDetailResponse.Category category = d.category() == null ? null
                : new ObjectDetailResponse.Category(d.category().code(), d.category().namePt(),
                        d.category().nameEn(), d.object().categoryBasis());
        List<ObjectDetailResponse.SourceType> types = d.sourceTypes().stream()
                .map(t -> new ObjectDetailResponse.SourceType(t.sourceCode(), t.sourceRef(), t.sourceType(),
                        t.morphType()))
                .toList();
        List<ObjectDetailResponse.Identifier> identifiers = d.identifiers().stream()
                .map(i -> new ObjectDetailResponse.Identifier(i.identifier(), i.catalog(), i.isCommonName(),
                        i.sourceCode()))
                .toList();
        ObjectDetailResponse.Position position = d.position()
                .map(p -> new ObjectDetailResponse.Position(p.raDeg(), p.decDeg(), p.frame(), p.epoch(),
                        p.sourceCode(), reference(p.bibcode()), p.retrievedAt()))
                .orElse(null);
        List<ObjectDetailResponse.Property> properties = d.properties().stream().map(ObjectDetailMapper::property)
                .toList();
        List<ObjectDetailResponse.Source> sources = d.sources().stream()
                .map(s -> new ObjectDetailResponse.Source(s.code(), s.name(), s.homepageUrl(), s.termsUrl(),
                        s.acknowledgment()))
                .toList();
        return new ObjectDetailResponse(d.object().slug(), d.object().displayName(), category,
                d.object().existenceStatus(), types, identifiers, position, properties, sources);
    }

    private static ObjectDetailResponse.Property property(PropertyView p) {
        ObjectDetailResponse.Absence absence = p.available() ? null
                : new ObjectDetailResponse.Absence(p.absenceReason().name(), p.absenceReason().messagePt(),
                        p.absenceSnr());
        return new ObjectDetailResponse.Property(p.code(), p.labelPt(), p.displayGroup(), p.qualifier(),
                p.available(), absence, p.preferred() == null ? null : measurement(p.preferred()),
                p.alternatives().stream().map(ObjectDetailMapper::measurement).toList());
    }

    private static ObjectDetailResponse.Measurement measurement(MeasurementView m) {
        return new ObjectDetailResponse.Measurement(m.id(), m.valueNum(), m.errMinus(), m.errPlus(), m.limitFlag(),
                m.valueText(), m.canonicalUnit(), m.originalValue(), m.originalUnit(), m.originalError(), m.epistemic(), m.method(),
                m.derivedFrom(), m.sourceCode(), m.dataset(), m.sourceUrl(), reference(m.bibcode()),
                m.retrievedAt());
    }

    /** Bibcode tem {@code &} (ex. {@code 2007A&A...474..653V}): precisa de encode na URL do ADS. */
    static ObjectDetailResponse.Reference reference(String bibcode) {
        if (bibcode == null) {
            return null;
        }
        return new ObjectDetailResponse.Reference(bibcode, ADS_ABSTRACT.formatted(Texts.urlEncode(bibcode)));
    }
}
