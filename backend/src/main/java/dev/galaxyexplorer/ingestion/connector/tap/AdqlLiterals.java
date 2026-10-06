package dev.galaxyexplorer.ingestion.connector.tap;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Unico lugar onde texto vira literal ADQL. Nunca concatenar valor externo
 * numa consulta fora daqui.
 */
public final class AdqlLiterals {

    private AdqlLiterals() {
    }

    /**
     * Envolve em aspas simples e dobra aspas simples internas ({@code Barnard's Star}
     * -> {@code 'Barnard''s Star'}).
     *
     * @throws IllegalArgumentException se nulo ou com caractere de controle
     */
    public static String quote(String value) {
        if (value == null) {
            throw new IllegalArgumentException("literal ADQL nulo");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException("literal ADQL com caractere de controle (posicao " + i + ")");
            }
        }
        return "'" + value.replace("'", "''") + "'";
    }

    /** {@code ('a', 'b')} na ordem recebida. */
    public static String inList(Collection<String> values) {
        requireNotEmpty(values);
        return values.stream().map(AdqlLiterals::quote).collect(Collectors.joining(", ", "(", ")"));
    }

    /** {@code (1, 2, 3)} — numeros nunca precisam de aspas, e por isso nunca carregam texto externo. */
    public static String inListNumeric(Collection<Long> values) {
        requireNotEmpty(values);
        return values.stream().map(v -> Long.toString(v)).collect(Collectors.joining(", ", "(", ")"));
    }

    private static void requireNotEmpty(Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("lista IN vazia");
        }
    }
}
