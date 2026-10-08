package dev.galaxyexplorer.api.category;

import java.util.List;

import dev.galaxyexplorer.api.image.ThumbnailDto;

/** Contrato JSON de {@code GET /api/v1/categories/{code}/objects}. */
public record CategoryObjectsResponse(Category category, long total, int limit, int offset, List<Item> items) {

    public record Category(String code, String namePt, String nameEn) {
    }

    /**
     * Item da listagem. Nao reaproveita {@code SearchHitDto}: matchType/score nao fazem sentido aqui.
     *
     * @param categoryCode categoria do proprio objeto (pode ser filha da pedida); pode ser {@code null}
     * @param categoryName pode ser {@code null}
     * @param thumbnail    miniatura do hero; {@code null} se o objeto nao tem imagem
     */
    public record Item(String slug, String displayName, String categoryCode, String categoryName,
                       ThumbnailDto thumbnail) {
    }
}
