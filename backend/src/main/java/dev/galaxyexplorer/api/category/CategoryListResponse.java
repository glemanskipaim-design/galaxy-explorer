package dev.galaxyexplorer.api.category;

import java.util.List;

/** Contrato JSON de {@code GET /api/v1/categories}: categorias raiz com contagem de objetos. */
public record CategoryListResponse(List<Item> categories) {

    /** @param objectCount objetos da subarvore (raiz + descendentes); 0 quando nao ha nenhum */
    public record Item(String code, String namePt, String nameEn, long objectCount) {
    }
}
