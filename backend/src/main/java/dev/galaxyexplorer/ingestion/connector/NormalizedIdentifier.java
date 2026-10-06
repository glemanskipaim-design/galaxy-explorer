package dev.galaxyexplorer.ingestion.connector;

/**
 * Identificador como a fonte escreve. O conector NAO calcula {@code normalized}
 * nem {@code catalog}: isso e do {@code CatalogWriter} (uma normalizacao so no sistema).
 */
public record NormalizedIdentifier(String identifier, boolean isCommonName) {
}
