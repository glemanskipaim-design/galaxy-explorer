package dev.galaxyexplorer.catalog.write;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.galaxyexplorer.catalog.category.CategoryDecision;
import dev.galaxyexplorer.catalog.category.CategoryRepository;
import dev.galaxyexplorer.catalog.category.CategoryResolver;
import dev.galaxyexplorer.catalog.category.CategoryRow;
import dev.galaxyexplorer.catalog.derived.DerivedValueService;
import dev.galaxyexplorer.catalog.identifier.IdentifierNormalizer;
import dev.galaxyexplorer.catalog.identifier.IdentifierRepository;
import dev.galaxyexplorer.catalog.identifier.IdentifierRow;
import dev.galaxyexplorer.catalog.measurement.MeasurementKey;
import dev.galaxyexplorer.catalog.measurement.MeasurementRepository;
import dev.galaxyexplorer.catalog.measurement.MeasurementWrite;
import dev.galaxyexplorer.catalog.measurement.PreferenceService;
import dev.galaxyexplorer.catalog.object.CelestialObjectRepository;
import dev.galaxyexplorer.catalog.object.DisplayNameRule;
import dev.galaxyexplorer.catalog.object.ObjectRow;
import dev.galaxyexplorer.catalog.object.ObjectSourceTypeRepository;
import dev.galaxyexplorer.catalog.object.SlugFactory;
import dev.galaxyexplorer.catalog.position.PositionRepository;
import dev.galaxyexplorer.catalog.position.PositionWrite;
import dev.galaxyexplorer.catalog.reference.BibReferenceRepository;
import dev.galaxyexplorer.catalog.source.SourceRow;
import dev.galaxyexplorer.catalog.source.SourceRepository;

/**
 * Grava no catalogo tudo o que uma fonte disse sobre um objeto. NAO abre
 * transacao: o chamador ({@code IngestionPipeline}) e o unico dono da fronteira
 * transacional — uma transacao por objeto.
 *
 * <p>Identidade entre execucoes: {@code (source_id, source_ref)} em
 * {@code object_source_type}. Ja existe -> atualiza o mesmo objeto (mesmo id e
 * slug); nao existe -> cria.
 */
@Service
public class CatalogWriter {

    private final SourceRepository sources;
    private final ObjectSourceTypeRepository sourceTypes;
    private final CelestialObjectRepository objects;
    private final CategoryRepository categories;
    private final CategoryResolver categoryResolver;
    private final IdentifierRepository identifiers;
    private final BibReferenceRepository references;
    private final PositionRepository positions;
    private final MeasurementRepository measurements;
    private final DerivedValueService derivedValues;
    private final PreferenceService preferences;

    public CatalogWriter(SourceRepository sources, ObjectSourceTypeRepository sourceTypes,
                         CelestialObjectRepository objects, CategoryRepository categories,
                         CategoryResolver categoryResolver, IdentifierRepository identifiers,
                         BibReferenceRepository references, PositionRepository positions,
                         MeasurementRepository measurements, DerivedValueService derivedValues,
                         PreferenceService preferences) {
        this.sources = sources;
        this.sourceTypes = sourceTypes;
        this.objects = objects;
        this.categories = categories;
        this.categoryResolver = categoryResolver;
        this.identifiers = identifiers;
        this.references = references;
        this.positions = positions;
        this.measurements = measurements;
        this.derivedValues = derivedValues;
        this.preferences = preferences;
    }

    public WriteResult write(ObjectWrite w) {
        SourceRow source = sources.getByCode(w.source());
        short sourceId = source.id();

        // 1. categoria e nome exibido
        CategoryRow other = categories.findByCode("other")
                .orElseThrow(() -> new IllegalStateException("categoria 'other' ausente — V3 nao aplicada?"));
        CategoryDecision category = categoryResolver.resolve(source.code(), w.sourceType(), w.morphType(),
                categories.findRules(sourceId), other);
        String displayName = DisplayNameRule.choose(w.sourceMainId(),
                w.identifiers().stream().map(IdentifierWrite::identifier).toList());

        // 2. identidade: (fonte, id na fonte)
        UUID objectId;
        String slug;
        boolean created;
        var existing = sourceTypes.findObjectIdBySourceRef(sourceId, w.sourceRef());
        if (existing.isPresent()) {
            objectId = existing.get();
            objects.updateClassification(objectId, displayName, category.categoryId(), category.basis());
            slug = objects.findById(objectId).map(ObjectRow::slug).orElseThrow();
            created = false;
        } else {
            slug = uniqueSlug(displayName);
            objectId = objects.insert(slug, displayName, category.categoryId(), category.basis());
            created = true;
        }
        sourceTypes.upsert(objectId, sourceId, w.sourceRef(), w.sourceType(), w.morphType(), w.runId());

        // 3. identificadores (normalizacao unica do sistema)
        Map<String, IdentifierRow> rows = new LinkedHashMap<>();
        for (IdentifierWrite id : w.identifiers()) {
            if (id.identifier() == null || id.identifier().isBlank()) {
                continue;
            }
            String normalized = IdentifierNormalizer.normalize(id.identifier());
            rows.putIfAbsent(normalized, new IdentifierRow(id.identifier(), normalized,
                    IdentifierNormalizer.catalogOf(id.identifier()), null, id.isCommonName(), source.code()));
        }
        identifiers.upsertAll(objectId, sourceId, new ArrayList<>(rows.values()));
        identifiers.deleteStaleForSource(objectId, sourceId, rows.keySet());

        // 4. referencias bibliograficas (bibcode -> reference_id so aqui)
        Set<String> bibcodes = new HashSet<>();
        w.position().map(PositionWrite::bibcode).ifPresent(bibcodes::add);
        w.measurements().stream().map(MeasurementWrite::bibcode).filter(b -> b != null).forEach(bibcodes::add);
        Map<String, Long> referenceIds = references.upsertBibcodes(bibcodes);

        // 5. posicao
        if (w.position().isPresent()) {
            PositionWrite p = w.position().get();
            positions.replaceForSource(objectId, sourceId, p, p.bibcode() == null ? null : referenceIds.get(p.bibcode()));
        } else {
            positions.deleteForSource(objectId, sourceId);
        }

        // 6. medicoes + limpeza do que sumiu da fonte
        Set<MeasurementKey> keep = new HashSet<>();
        for (MeasurementWrite m : w.measurements()) {
            Long refId = m.bibcode() == null ? null : referenceIds.get(m.bibcode());
            measurements.upsert(objectId, sourceId, m.withReferenceId(refId));
            keep.add(m.key(sourceId));
        }
        int deleted = measurements.deleteStale(objectId, sourceId, keep);

        // 7. derivados e preferidos (passo 7 da secao 8 do doc), na mesma transacao
        derivedValues.recompute(objectId);
        preferences.recompute(objectId);

        return new WriteResult(objectId, slug, created, w.measurements().size(), deleted);
    }

    private String uniqueSlug(String displayName) {
        String base = SlugFactory.baseSlug(displayName);
        String candidate = base;
        int n = 2;
        while (objects.slugExists(candidate)) {
            candidate = SlugFactory.withSuffix(base, n++);
        }
        return candidate;
    }
}
