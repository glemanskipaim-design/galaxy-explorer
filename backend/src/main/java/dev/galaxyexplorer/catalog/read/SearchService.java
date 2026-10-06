package dev.galaxyexplorer.catalog.read;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.galaxyexplorer.catalog.category.CategoryRepository;
import dev.galaxyexplorer.catalog.category.CategoryRow;
import dev.galaxyexplorer.catalog.identifier.IdentifierMatch;
import dev.galaxyexplorer.catalog.identifier.IdentifierNormalizer;
import dev.galaxyexplorer.catalog.identifier.IdentifierRepository;
import dev.galaxyexplorer.catalog.object.CelestialObjectRepository;
import dev.galaxyexplorer.catalog.object.ObjectRow;

/**
 * Busca local por identificador: igualdade na chave normalizada primeiro; se
 * nao houver, similaridade de trigramas. Um hit por objeto.
 */
@Service
public class SearchService {

    public static final int MAX_LIMIT = 20;
    public static final double MIN_SIMILARITY = 0.3;

    private final IdentifierRepository identifiers;
    private final CelestialObjectRepository objects;
    private final CategoryRepository categories;

    public SearchService(IdentifierRepository identifiers, CelestialObjectRepository objects,
                         CategoryRepository categories) {
        this.identifiers = identifiers;
        this.objects = objects;
        this.categories = categories;
    }

    public List<SearchHit> search(String rawQuery, int limit) {
        int max = Math.max(1, Math.min(limit, MAX_LIMIT));
        String normalized = IdentifierNormalizer.normalize(rawQuery);
        // Busca mais linhas que o limite: varios identificadores podem ser do mesmo objeto.
        List<IdentifierMatch> matches = identifiers.findExact(normalized, max * 5);
        MatchType type = MatchType.EXACT;
        if (matches.isEmpty()) {
            matches = identifiers.findFuzzy(normalized, MIN_SIMILARITY, max * 5);
            type = MatchType.FUZZY;
        }
        Map<UUID, IdentifierMatch> bestPerObject = new LinkedHashMap<>();
        for (IdentifierMatch m : matches) {
            bestPerObject.merge(m.objectId(), m, (a, b) -> b.score() > a.score() ? b : a);
        }
        Map<UUID, ObjectRow> rows = objects.findByIds(bestPerObject.keySet()).stream()
                .collect(Collectors.toMap(ObjectRow::id, Function.identity()));
        Map<Integer, Optional<CategoryRow>> categoryCache = new LinkedHashMap<>();
        List<SearchHit> hits = new ArrayList<>();
        for (IdentifierMatch m : bestPerObject.values()) {
            ObjectRow object = rows.get(m.objectId());
            if (object == null) {
                continue;
            }
            Optional<CategoryRow> category = object.categoryId() == null
                    ? Optional.empty()
                    : categoryCache.computeIfAbsent(object.categoryId(), categories::findById);
            hits.add(new SearchHit(object.id(), object.slug(), object.displayName(), category.map(CategoryRow::code).orElse(null),
                    category.map(CategoryRow::namePt).orElse(null), m.identifier(), type, m.score()));
            if (hits.size() == max) {
                break;
            }
        }
        return hits;
    }
}
