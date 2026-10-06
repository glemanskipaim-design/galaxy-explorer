package dev.galaxyexplorer.catalog.identifier;

/**
 * Identificador de um objeto como gravado em {@code object_identifier}.
 *
 * @param catalog    primeiro token ({@code 'M'}, {@code 'NGC'}, {@code 'NAME'}); pode ser {@code null}
 * @param lang       idioma do nome popular; {@code null} para identificadores de catalogo
 * @param sourceCode fonte que forneceu o identificador; {@code null} = alias curado manualmente
 */
public record IdentifierRow(String identifier, String normalized, String catalog, String lang,
                            boolean isCommonName, String sourceCode) {
}
