package dev.galaxyexplorer.catalog.category;

/**
 * Uma linha de {@code source_type_mapping}.
 *
 * @param morphPattern regex Java aplicada com {@code find()} ao {@code morph_type}; {@code ""} = qualquer
 */
public record TypeMappingRule(int id, String sourceType, String morphPattern, int categoryId, String categoryCode,
                              short priority) {
}
