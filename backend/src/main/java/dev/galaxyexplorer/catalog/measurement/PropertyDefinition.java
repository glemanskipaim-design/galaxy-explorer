package dev.galaxyexplorer.catalog.measurement;

/** Uma linha de {@code property_definition}. {@code canonicalUnit} e {@code null} para adimensionais/texto. */
public record PropertyDefinition(String code, String labelPt, String canonicalUnit, String valueKind,
                                 String displayGroup) {
}
