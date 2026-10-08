package dev.galaxyexplorer.api.search;

import dev.galaxyexplorer.api.image.ThumbnailDto;

/**
 * @param categoryCode pode ser {@code null}
 * @param categoryName pode ser {@code null}
 * @param matchType    {@code EXACT} ou {@code FUZZY}
 * @param thumbnail    miniatura do hero; {@code null} se o objeto nao tem imagem
 */
public record SearchHitDto(String slug, String displayName, String categoryCode, String categoryName,
                           String matchedIdentifier, String matchType, double score, ThumbnailDto thumbnail) {
}
