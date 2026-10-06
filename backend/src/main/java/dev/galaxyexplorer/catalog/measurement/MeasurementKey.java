package dev.galaxyexplorer.catalog.measurement;

/** Espelha o UNIQUE de {@code measurement}: (objeto, propriedade, qualificador, fonte, dataset). */
public record MeasurementKey(String propertyCode, String qualifier, short sourceId, String dataset) {
}
