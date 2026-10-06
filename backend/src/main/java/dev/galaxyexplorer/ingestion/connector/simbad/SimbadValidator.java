package dev.galaxyexplorer.ingestion.connector.simbad;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.galaxyexplorer.ingestion.connector.tap.TapRow;

/**
 * Validacao de valores do SIMBAD. Puro.
 * <ul>
 *   <li>falta de {@code main_id} ou {@code otype} rejeita o objeto;</li>
 *   <li>valor fora de faixa fisica descarta SO o valor (com {@code issue});</li>
 *   <li>paralaxe &lt;= 0 e guardada (e uma medicao real), so nao gera distancia.</li>
 * </ul>
 */
public final class SimbadValidator {

    private SimbadValidator() {
    }

    /** Problemas que rejeitam o objeto inteiro. Lista vazia = ok. */
    public static List<String> validateBasic(TapRow basic) {
        List<String> problems = new ArrayList<>();
        if (basic.text("main_id").isEmpty()) {
            problems.add("basic sem main_id");
        }
        if (basic.text("otype").isEmpty()) {
            problems.add("basic sem otype");
        }
        return problems;
    }

    public static Optional<String> checkRa(double ra) {
        return ra >= 0.0 && ra < 360.0 ? Optional.empty() : Optional.of("ra fora de [0,360): " + ra);
    }

    public static Optional<String> checkDec(double dec) {
        return dec >= -90.0 && dec <= 90.0 ? Optional.empty() : Optional.of("dec fora de [-90,90]: " + dec);
    }

    public static Optional<String> checkMagnitude(String band, double mag) {
        return mag >= -30.0 && mag <= 40.0 ? Optional.empty()
                : Optional.of("magnitude " + band + " fora de [-30,40]: " + mag);
    }

    public static Optional<String> checkRedshift(double z) {
        return z > -1.0 ? Optional.empty() : Optional.of("redshift <= -1: " + z);
    }
}
