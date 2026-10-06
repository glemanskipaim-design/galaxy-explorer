package dev.galaxyexplorer.ingestion.connector.tap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decodifica a resposta JSON de um servico TAP. Usa a API de streaming do
 * Jackson para guardar o texto exato de cada numero (um {@code double} perderia
 * a grafia original: o inteiro {@code 7} de M1 viraria {@code 7.0}). Puro.
 */
public final class TapJsonParser {

    /**
     * {@code readTree} e chamado no meio do stream (so para {@code metadata}); o Jackson 3 liga
     * {@code FAIL_ON_TRAILING_TOKENS} por padrao, o que acusaria o resto do documento como "lixo".
     */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private TapJsonParser() {
    }

    /** @throws SourceQueryException se o JSON for invalido ou faltar {@code metadata}/{@code data} */
    public static TapResult parse(String json) {
        try (JsonParser p = MAPPER.createParser(json)) {
            if (p.nextToken() != JsonToken.START_OBJECT) {
                throw new SourceQueryException("resposta TAP não é um objeto JSON");
            }
            List<TapColumn> columns = null;
            List<List<TapRow.Cell>> data = null;
            while (p.nextToken() == JsonToken.PROPERTY_NAME) {
                String field = p.currentName();
                p.nextToken();
                if ("metadata".equals(field)) {
                    columns = readColumns(MAPPER.readTree(p));
                } else if ("data".equals(field)) {
                    data = readData(p);
                } else {
                    p.skipChildren();
                }
            }
            if (columns == null || data == null) {
                throw new SourceQueryException("resposta TAP sem 'metadata' ou 'data'");
            }
            return new TapResult(columns, toRows(columns, data));
        } catch (JacksonException e) {
            throw new SourceQueryException("resposta TAP com JSON inválido: " + e.getOriginalMessage(), e);
        }
    }

    private static List<TapColumn> readColumns(JsonNode metadata) {
        if (!metadata.isArray()) {
            throw new SourceQueryException("'metadata' da resposta TAP não é uma lista");
        }
        List<TapColumn> columns = new ArrayList<>();
        for (JsonNode c : metadata.values()) {
            columns.add(new TapColumn(c.path("name").asString(), textOrNull(c, "unit"), textOrNull(c, "datatype"),
                    textOrNull(c, "ucd")));
        }
        return columns;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static List<List<TapRow.Cell>> readData(JsonParser p) {
        if (p.currentToken() != JsonToken.START_ARRAY) {
            throw new SourceQueryException("'data' da resposta TAP não é uma lista");
        }
        List<List<TapRow.Cell>> rows = new ArrayList<>();
        while (p.nextToken() == JsonToken.START_ARRAY) {
            List<TapRow.Cell> row = new ArrayList<>();
            JsonToken t;
            while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                row.add(switch (t) {
                    case VALUE_NULL -> new TapRow.Cell(null);
                    case VALUE_STRING -> new TapRow.Cell(p.getString());
                    case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT, VALUE_TRUE, VALUE_FALSE ->
                            new TapRow.Cell(p.getString());
                    default -> throw new SourceQueryException("valor inesperado em 'data' da resposta TAP: " + t);
                });
            }
            rows.add(row);
        }
        if (p.currentToken() != JsonToken.END_ARRAY) {
            throw new SourceQueryException("linha de 'data' da resposta TAP não é uma lista");
        }
        return rows;
    }

    private static List<TapRow> toRows(List<TapColumn> columns, List<List<TapRow.Cell>> data) {
        List<TapRow> rows = new ArrayList<>(data.size());
        for (List<TapRow.Cell> cells : data) {
            if (cells.size() != columns.size()) {
                throw new SourceQueryException("linha TAP com " + cells.size() + " valores para " + columns.size()
                        + " colunas");
            }
            Map<String, TapRow.Cell> byName = new HashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                byName.put(columns.get(i).name().toLowerCase(Locale.ROOT), cells.get(i));
            }
            rows.add(new TapRow(byName));
        }
        return rows;
    }
}
