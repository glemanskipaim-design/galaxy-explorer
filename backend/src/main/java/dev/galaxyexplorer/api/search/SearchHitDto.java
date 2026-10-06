package dev.galaxyexplorer.api.search;

/**
 * @param categoryCode pode ser {@code null}
 * @param categoryName pode ser {@code null}
 * @param matchType    {@code EXACT} ou {@code FUZZY}
 */
public record SearchHitDto(String slug, String displayName, String categoryCode, String categoryName,
                           String matchedIdentifier, String matchType, double score) {
}
