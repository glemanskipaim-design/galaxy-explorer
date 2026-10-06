package dev.galaxyexplorer.catalog.object;

/**
 * Tipo bruto de um objeto segundo uma fonte, sem interpretacao.
 *
 * @param sourceRef id do objeto na fonte (oid do SIMBAD)
 * @param morphType pode ser {@code null}
 */
public record SourceTypeRow(String sourceCode, String sourceRef, String sourceType, String morphType) {
}
