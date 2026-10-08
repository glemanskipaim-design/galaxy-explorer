package dev.galaxyexplorer.ingestion.connector.esa;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON AVM da ESA ({@code /images/<id>/api/json/}) -> {@link AvmRecord}.
 * Forma desconhecida em qualquer campo usado = {@link IllegalArgumentException}
 * (a candidata e rejeitada com esse motivo). Puro.
 */
public final class AvmJsonParser {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}.*");

    private AvmJsonParser() {
    }

    public static boolean isJson(String body) {
        try {
            return MAPPER.readTree(body).isObject();
        } catch (JacksonException e) {
            return false;
        }
    }

    /** @throws IllegalArgumentException JSON invalido, {@code ID} diferente do pedido ou campo em forma desconhecida */
    public static AvmRecord parse(String expectedId, String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("AVM não é JSON válido: " + e.getOriginalMessage(), e);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("AVM não é um objeto JSON");
        }
        String id = AvmValues.text(root.get("ID"));
        if (!expectedId.equals(id)) {
            throw new IllegalArgumentException("AVM com ID '" + id + "', pedido '" + expectedId + "'");
        }
        double[] ref = pair(root.get("Spatial.ReferenceValue"), "Spatial.ReferenceValue");
        return new AvmRecord(id, AvmValues.text(root.get("Type")), AvmValues.text(root.get("Title")),
                AvmValues.text(root.get("Credit")), AvmValues.text(root.get("Rights")),
                AvmValues.list(root.get("Subject.Name")), AvmValues.list(root.get("Facility")),
                AvmValues.list(root.get("Instrument")), AvmValues.list(root.get("Spectral.Band")),
                ref == null ? null : ref[0], ref == null ? null : ref[1],
                pair(root.get("Spatial.Scale"), "Spatial.Scale"),
                pair(root.get("Spatial.ReferenceDimension"), "Spatial.ReferenceDimension"),
                pair(root.get("Spatial.ReferencePixel"), "Spatial.ReferencePixel"),
                AvmValues.text(root.get("Spatial.CoordinateFrame")), date(AvmValues.text(root.get("Date"))),
                AvmValues.text(root.get("ReferenceURL")));
    }

    /** Lista de 2 numeros (como texto, forma real); lista vazia/ausente = {@code null}. */
    static double[] pair(JsonNode node, String field) {
        List<String> values = AvmValues.list(node);
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() != 2) {
            throw new IllegalArgumentException(field + " com " + values.size() + " valores, esperado 2");
        }
        try {
            double a = Double.parseDouble(values.get(0));
            double b = Double.parseDouble(values.get(1));
            if (!Double.isFinite(a) || !Double.isFinite(b)) {
                throw new IllegalArgumentException(field + " não finito: " + values);
            }
            return new double[] {a, b};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " não numérico: " + values, e);
        }
    }

    static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        if (!ISO_DATE.matcher(value).matches()) {
            throw new IllegalArgumentException("Date em formato desconhecido: '" + value + "'");
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date inválida: '" + value + "'", e);
        }
    }
}
