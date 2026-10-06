package dev.galaxyexplorer.ingestion.connector.tap;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Resposta TAP em JSON ({@code {"metadata":[...],"data":[[...]]}}) ja decodificada. */
public record TapResult(List<TapColumn> columns, List<TapRow> rows) {

    public TapResult {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    /** Nomes comparados em minusculas. */
    public Optional<TapColumn> column(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return columns.stream().filter(c -> c.name().toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }

    public int size() {
        return rows.size();
    }
}
