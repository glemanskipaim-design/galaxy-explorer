package dev.galaxyexplorer.catalog.read;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.galaxyexplorer.catalog.category.CategoryRepository;
import dev.galaxyexplorer.catalog.category.CategoryRow;
import dev.galaxyexplorer.catalog.derived.DistanceComputation;
import dev.galaxyexplorer.catalog.derived.DistanceFromParallax;
import dev.galaxyexplorer.catalog.identifier.IdentifierRepository;
import dev.galaxyexplorer.catalog.identifier.IdentifierRow;
import dev.galaxyexplorer.catalog.image.ObjectImageRepository;
import dev.galaxyexplorer.catalog.image.ObjectImageRow;
import dev.galaxyexplorer.catalog.measurement.MeasurementRepository;
import dev.galaxyexplorer.catalog.measurement.MeasurementRow;
import dev.galaxyexplorer.catalog.measurement.PropertyDefinition;
import dev.galaxyexplorer.catalog.measurement.PropertyDefinitionRepository;
import dev.galaxyexplorer.catalog.object.CelestialObjectRepository;
import dev.galaxyexplorer.catalog.object.ObjectRow;
import dev.galaxyexplorer.catalog.object.ObjectSourceTypeRepository;
import dev.galaxyexplorer.catalog.object.SourceTypeRow;
import dev.galaxyexplorer.catalog.position.PositionRepository;
import dev.galaxyexplorer.catalog.position.PositionRow;
import dev.galaxyexplorer.catalog.source.SourceRepository;
import dev.galaxyexplorer.catalog.source.SourceRow;

/** Monta o detalhe de um objeto: valores com proveniencia e ausencias com motivo. */
@Service
public class ObjectQueryService {

    private final CelestialObjectRepository objects;
    private final CategoryRepository categories;
    private final ObjectSourceTypeRepository sourceTypes;
    private final IdentifierRepository identifiers;
    private final PositionRepository positions;
    private final MeasurementRepository measurements;
    private final PropertyDefinitionRepository propertyDefinitions;
    private final SourceRepository sources;
    private final ObjectImageRepository images;

    public ObjectQueryService(CelestialObjectRepository objects, CategoryRepository categories,
                              ObjectSourceTypeRepository sourceTypes, IdentifierRepository identifiers,
                              PositionRepository positions, MeasurementRepository measurements,
                              PropertyDefinitionRepository propertyDefinitions, SourceRepository sources,
                              ObjectImageRepository images) {
        this.images = images;
        this.objects = objects;
        this.categories = categories;
        this.sourceTypes = sourceTypes;
        this.identifiers = identifiers;
        this.positions = positions;
        this.measurements = measurements;
        this.propertyDefinitions = propertyDefinitions;
        this.sources = sources;
    }

    public Optional<ObjectDetail> findBySlug(String slug) {
        Optional<ObjectRow> found = objects.findBySlug(slug);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ObjectRow object = found.get();
        UUID id = object.id();
        CategoryRow category = object.categoryId() == null ? null
                : categories.findById(object.categoryId()).orElse(null);
        List<SourceTypeRow> types = sourceTypes.findByObject(id);
        List<IdentifierRow> ids = identifiers.findByObject(id);
        Optional<PositionRow> position = positions.findPreferred(id);
        List<MeasurementRow> rows = measurements.findByObject(id);

        List<PropertyView> properties = buildProperties(propertyDefinitions.findAll(), rows);

        TreeSet<String> sourceCodes = new TreeSet<>();
        types.forEach(t -> sourceCodes.add(t.sourceCode()));
        position.ifPresent(p -> sourceCodes.add(p.sourceCode()));
        rows.forEach(m -> sourceCodes.add(m.sourceCode()));
        List<ObjectImageRow> objectImages = images.findByObject(id);
        objectImages.forEach(i -> sourceCodes.add(i.sourceCode()));
        List<SourceRow> usedSources = sources.findByCodes(sourceCodes);
        ImageAbsenceReason absence = !objectImages.isEmpty() ? null
                : position.isEmpty() ? ImageAbsenceReason.NO_POSITION : ImageAbsenceReason.NOT_AVAILABLE;

        return Optional.of(new ObjectDetail(object, category, types, ids, position, properties, usedSources,
                objectImages, absence));
    }

    public List<String> slugsOf(Collection<UUID> ids) {
        return objects.findByIds(ids).stream().map(ObjectRow::slug).sorted().toList();
    }

    /**
     * Uma {@link PropertyView} por {@code (code, qualifier)} com medicao e uma
     * por propriedade definida sem nenhuma medicao ({@code available=false}).
     */
    static List<PropertyView> buildProperties(List<PropertyDefinition> definitions, List<MeasurementRow> rows) {
        Map<String, List<MeasurementRow>> byProperty = rows.stream()
                .collect(Collectors.groupingBy(MeasurementRow::propertyCode, LinkedHashMap::new, Collectors.toList()));
        List<PropertyView> views = new ArrayList<>();
        for (PropertyDefinition def : definitions) {
            List<MeasurementRow> forProperty = byProperty.getOrDefault(def.code(), List.of());
            if (forProperty.isEmpty()) {
                views.add(absent(def, rows));
                continue;
            }
            Map<String, List<MeasurementRow>> byQualifier = forProperty.stream()
                    .sorted(Comparator.comparing(MeasurementRow::qualifier))
                    .collect(Collectors.groupingBy(MeasurementRow::qualifier, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<String, List<MeasurementRow>> q : byQualifier.entrySet()) {
                MeasurementView preferred = null;
                List<MeasurementView> alternatives = new ArrayList<>();
                for (MeasurementRow m : q.getValue()) {
                    MeasurementView view = toView(m, def.canonicalUnit());
                    if (m.preferred() && preferred == null) {
                        preferred = view;
                    } else {
                        alternatives.add(view);
                    }
                }
                views.add(new PropertyView(def.code(), def.labelPt(), def.displayGroup(), q.getKey(), true, null,
                        null, preferred, alternatives));
            }
        }
        return views;
    }

    /**
     * Ausencia com motivo. Para {@code distance} com paralaxe presente, o
     * motivo vem de {@link DistanceFromParallax#compute} reaplicado aqui — a
     * regra de S/N existe em um lugar so.
     */
    private static PropertyView absent(PropertyDefinition def, List<MeasurementRow> rows) {
        AbsenceReason reason = AbsenceReason.NOT_PROVIDED_BY_SOURCE;
        Double snr = null;
        if ("distance".equals(def.code())) {
            Optional<MeasurementRow> parallax = rows.stream()
                    .filter(m -> "parallax".equals(m.propertyCode()) && m.valueNum() != null)
                    .min(Comparator.comparing((MeasurementRow m) -> !m.preferred()).thenComparingLong(MeasurementRow::id));
            if (parallax.isPresent()) {
                DistanceComputation result = DistanceFromParallax.compute(parallax.get().valueNum(),
                        parallax.get().errMinus());
                if (result instanceof DistanceComputation.NotComputed nc) {
                    snr = nc.snr();
                    switch (nc.reason()) {
                        // err nulo + original_error preenchido = a fonte publicou incerteza invalida
                        case NO_UNCERTAINTY -> reason = parallax.get().originalError() == null
                                ? AbsenceReason.PARALLAX_WITHOUT_UNCERTAINTY
                                : AbsenceReason.PARALLAX_UNCERTAINTY_INVALID;
                        case NON_POSITIVE_PARALLAX -> reason = AbsenceReason.PARALLAX_NON_POSITIVE;
                        case SNR_BELOW_THRESHOLD -> reason = AbsenceReason.PARALLAX_SNR_BELOW_5;
                        default -> reason = AbsenceReason.NOT_PROVIDED_BY_SOURCE;
                    }
                }
            }
        }
        return new PropertyView(def.code(), def.labelPt(), def.displayGroup(), "", false, reason, snr, null,
                List.of());
    }

    private static MeasurementView toView(MeasurementRow m, String canonicalUnit) {
        return new MeasurementView(m.id(), m.valueNum(), m.errMinus(), m.errPlus(), m.limitFlag(), m.valueText(),
                canonicalUnit, m.originalValue(), m.originalUnit(), m.originalError(), m.epistemic(), m.method(), m.derivedFrom(),
                m.sourceCode(), m.dataset(), m.sourceUrl(), m.bibcode(), m.retrievedAt(), m.preferred());
    }
}
