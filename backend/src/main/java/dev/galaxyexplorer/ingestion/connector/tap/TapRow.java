package dev.galaxyexplorer.ingestion.connector.tap;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Uma linha de resposta TAP. Cada celula guarda o texto exatamente como estava
 * no JSON (numeros inclusive: {@code 7} continua {@code "7"}, nao {@code "7.0"}), para
 * que {@code original_value} seja fiel a fonte.
 *
 * <p>Pedir uma coluna que nao existe na resposta e erro de programacao
 * ({@link IllegalArgumentException}), nao dado ausente.
 */
public final class TapRow {

    /** @param raw texto exato; {@code null} para JSON null. */
    record Cell(String raw) {
    }

    private final Map<String, Cell> cells;

    TapRow(Map<String, Cell> cells) {
        this.cells = Collections.unmodifiableMap(cells);
    }

    private Cell cell(String column) {
        String key = column.toLowerCase(Locale.ROOT);
        if (!cells.containsKey(key)) {
            throw new IllegalArgumentException("coluna '" + column + "' ausente na resposta TAP " + cells.keySet());
        }
        return cells.get(key);
    }

    /** Valor como apareceu no JSON (sem trim), para {@code original_value}. Vazio se JSON null. */
    public Optional<String> rawText(String column) {
        return Optional.ofNullable(cell(column).raw());
    }

    /** Texto com trim; string vazia/em branco = vazio. */
    public Optional<String> text(String column) {
        String raw = cell(column).raw();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(raw.trim());
    }

    /** Numero; vazio se nulo, em branco ou nao numerico (use {@link #rawText} para distinguir). */
    public Optional<Double> number(String column) {
        return text(column).flatMap(TapRow::parseDouble);
    }

    /** Inteiro; vazio se nulo, em branco ou nao inteiro. */
    public Optional<Long> integer(String column) {
        return text(column).flatMap(s -> {
            try {
                return Optional.of(Long.parseLong(s));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Double> parseDouble(String s) {
        try {
            double value = Double.parseDouble(s);
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
