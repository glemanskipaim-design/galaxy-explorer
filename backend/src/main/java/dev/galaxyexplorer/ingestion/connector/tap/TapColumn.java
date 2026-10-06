package dev.galaxyexplorer.ingestion.connector.tap;

/**
 * Metadado de uma coluna de resposta TAP ({@code metadata[]} do JSON).
 *
 * @param unit     pode ser {@code null}
 * @param datatype pode ser {@code null}
 * @param ucd      pode ser {@code null}
 */
public record TapColumn(String name, String unit, String datatype, String ucd) {
}
