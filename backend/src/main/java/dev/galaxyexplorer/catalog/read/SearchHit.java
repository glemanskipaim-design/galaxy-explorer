package dev.galaxyexplorer.catalog.read;

import java.util.UUID;

/**
 * @param objectId       id interno (nao exposto pela API; usado pelo job de importacao)
 * @param categoryCode   pode ser {@code null}
 * @param categoryNamePt pode ser {@code null}
 */
public record SearchHit(UUID objectId, String slug, String displayName, String categoryCode, String categoryNamePt,
                        String matchedIdentifier, MatchType matchType, double score) {
}
