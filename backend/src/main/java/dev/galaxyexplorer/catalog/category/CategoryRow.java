package dev.galaxyexplorer.catalog.category;

/** @param parentId pode ser {@code null} (categoria raiz) */
public record CategoryRow(int id, String code, Integer parentId, String namePt, String nameEn) {
}
