package dev.galaxyexplorer.catalog.write;

/** Identificador como a fonte escreve ({@code 'M  31'}); a normalizacao e feita pelo {@code CatalogWriter}. */
public record IdentifierWrite(String identifier, boolean isCommonName) {
}
