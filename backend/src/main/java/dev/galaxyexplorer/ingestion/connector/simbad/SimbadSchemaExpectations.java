package dev.galaxyexplorer.ingestion.connector.simbad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.SchemaCheck;
import dev.galaxyexplorer.ingestion.connector.tap.TapColumn;
import dev.galaxyexplorer.ingestion.connector.tap.TapJsonParser;
import dev.galaxyexplorer.ingestion.connector.tap.TapResult;
import dev.galaxyexplorer.ingestion.connector.tap.TapRow;

/**
 * O que esperamos do esquema do SIMBAD. Puro.
 *
 * <p>As unidades esperadas foram COPIADAS da fixture capturada ao vivo
 * ({@code fixtures/simbad/tap_schema.json} e {@code sample_basic.json},
 * 2026-10-05), nao da memoria.
 */
public final class SimbadSchemaExpectations {

    /** tabela -> coluna -> unidade esperada (so colunas cuja unidade usamos). */
    static final Map<String, Map<String, String>> EXPECTED_UNITS = Map.of(
            SimbadQueries.DS_BASIC, Map.of(
                    "ra", "deg",
                    "dec", "deg",
                    "plx_value", "mas",
                    "plx_err", "mas",
                    "rvz_radvel", "km.s-1",
                    "rvz_err", "km.s-1",
                    "galdim_majaxis", "arcmin",
                    "galdim_minaxis", "arcmin"));

    private SimbadSchemaExpectations() {
    }

    /** Confere o {@code TAP_SCHEMA.columns}: toda coluna consultada existe e as unidades batem. */
    public static SchemaCheck evaluate(RawPayload tapSchemaPayload) {
        TapResult result = TapJsonParser.parse(tapSchemaPayload.body());
        Map<String, Map<String, String>> unitsByTable = new HashMap<>();
        for (TapRow row : result.rows()) {
            String table = row.text("table_name").orElse("");
            String column = row.text("column_name").orElse("");
            unitsByTable.computeIfAbsent(table, t -> new HashMap<>()).put(column, row.text("unit").orElse(null));
        }
        List<String> problems = new ArrayList<>();
        SimbadQueries.COLUMNS_BY_TABLE.forEach((table, columns) -> {
            Map<String, String> present = unitsByTable.getOrDefault(table, Map.of());
            for (String column : columns) {
                if (!present.containsKey(column)) {
                    problems.add("SCHEMA_MISSING " + table + "." + column);
                }
            }
            EXPECTED_UNITS.getOrDefault(table, Map.of()).forEach((column, unit) -> {
                if (present.containsKey(column) && !Objects.equals(unit, present.get(column))) {
                    problems.add("UNIT_MISMATCH " + table + "." + column + ": esperado " + unit + ", veio "
                            + present.get(column));
                }
            });
        });
        problems.sort(null);
        return new SchemaCheck(problems.isEmpty(), problems, tapSchemaPayload);
    }

    /** Confere o {@code unit} do {@code metadata} de uma resposta contra o esperado para o dataset. */
    public static List<String> unitProblems(String dataset, TapResult result) {
        List<String> problems = new ArrayList<>();
        EXPECTED_UNITS.getOrDefault(dataset, Map.of()).forEach((column, unit) -> {
            String actual = result.column(column).map(TapColumn::unit).orElse(null);
            if (result.column(column).isPresent() && !Objects.equals(unit, actual)) {
                problems.add("UNIT_MISMATCH " + dataset + "." + column + ": esperado " + unit + ", veio " + actual);
            }
        });
        problems.sort(null);
        return problems;
    }
}
