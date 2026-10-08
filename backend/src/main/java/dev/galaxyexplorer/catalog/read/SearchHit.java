package dev.galaxyexplorer.catalog.read;

import java.util.UUID;

import dev.galaxyexplorer.catalog.image.HeroThumb;

/**
 * @param objectId       id interno (nao exposto pela API; usado pelo job de importacao)
 * @param categoryCode   pode ser {@code null}
 * @param categoryNamePt pode ser {@code null}
 * @param thumbnail      miniatura do hero; {@code null} se o objeto nao tem imagem
 */
public record SearchHit(UUID objectId, String slug, String displayName, String categoryCode, String categoryNamePt,
                        String matchedIdentifier, MatchType matchType, double score, HeroThumb thumbnail) {
}
