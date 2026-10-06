package dev.galaxyexplorer.catalog.category;

/** Categoria escolhida e o raciocinio por tras dela (vai para {@code celestial_object.category_basis}). */
public record CategoryDecision(int categoryId, String categoryCode, String basis) {
}
