package dev.galaxyexplorer.api.search;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.galaxyexplorer.api.error.ApiErrorCode;
import dev.galaxyexplorer.api.error.ApiException;
import dev.galaxyexplorer.catalog.identifier.IdentifierNormalizer;
import dev.galaxyexplorer.catalog.read.SearchService;

/**
 * Busca local por identificador.
 *
 * <p>{@code GET /api/v1/search?q=<termo>}
 * <ul>
 *   <li>{@code q}: obrigatorio, 1..100 caracteres apos trim;</li>
 *   <li>200: {@code {query, normalizedQuery, hits:[{slug, displayName, categoryCode, categoryName,
 *       matchedIdentifier, matchType, score}]}} — lista vazia tambem e 200 (o frontend decide
 *       disparar importacao);</li>
 *   <li>400 {@code VALIDATION_FAILED}: {@code q} ausente, vazio ou longo demais.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    static final int MAX_QUERY_LENGTH = 100;

    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    @GetMapping
    public SearchResponse search(@RequestParam("q") String q) {
        String query = q.trim();
        if (query.isEmpty() || query.length() > MAX_QUERY_LENGTH) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "parâmetros inválidos",
                    List.of(Map.of("field", "q", "message", "deve ter de 1 a " + MAX_QUERY_LENGTH + " caracteres")));
        }
        String normalized;
        try {
            normalized = IdentifierNormalizer.normalize(query);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "parâmetros inválidos",
                    List.of(Map.of("field", "q", "message", "termo sem caracteres pesquisáveis")));
        }
        List<SearchHitDto> hits = search.search(query, SearchService.MAX_LIMIT).stream()
                .map(h -> new SearchHitDto(h.slug(), h.displayName(), h.categoryCode(), h.categoryNamePt(),
                        h.matchedIdentifier(), h.matchType().name(), h.score()))
                .toList();
        return new SearchResponse(query, normalized, hits);
    }
}
