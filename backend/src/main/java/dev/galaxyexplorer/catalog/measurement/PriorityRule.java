package dev.galaxyexplorer.catalog.measurement;

/**
 * Uma linha de {@code property_source_priority}: a politica de conflito como dado.
 *
 * @param categoryId {@code null} = vale para qualquer categoria
 * @param rank       1 = preferida
 */
public record PriorityRule(String propertyCode, Integer categoryId, short sourceId, short rank) {
}
