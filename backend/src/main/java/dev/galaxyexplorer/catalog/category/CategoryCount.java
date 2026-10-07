package dev.galaxyexplorer.catalog.category;

/**
 * Uma categoria raiz e quantos objetos do catalogo caem na sua subarvore
 * (a propria raiz mais todas as descendentes).
 */
public record CategoryCount(int id, String code, String namePt, String nameEn, long objectCount) {
}
