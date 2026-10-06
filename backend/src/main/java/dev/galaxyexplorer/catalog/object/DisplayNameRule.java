package dev.galaxyexplorer.catalog.object;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import dev.galaxyexplorer.common.Texts;

/**
 * Escolhe o nome exibido de um objeto a partir dos identificadores da fonte.
 * O {@code main_id} do SIMBAD nem sempre e o nome popular (M45 tem
 * {@code main_id = 'Cl Melotte   22'}), por isso a ordem:
 * <ol>
 *   <li>identificador Messier ({@code 'M  31'}) com espacos colapsados -> {@code 'M 31'};</li>
 *   <li>senao, o {@code main_id} se ele proprio for um {@code NAME ...} (o nome que a fonte
 *       escolheu como principal), sem o prefixo;</li>
 *   <li>senao, o {@code NAME ...} mais curto (empate: alfabetico), sem o prefixo;</li>
 *   <li>senao, o {@code main_id} com espacos colapsados.</li>
 * </ol>
 * O passo 2 existe por causa do dado real: Sgr A* tem {@code main_id = 'NAME Sgr A*'}, mas o
 * NAME mais curto e {@code 'NAME Sgr A'} — outra fonte (o complexo de radio maior).
 */
public final class DisplayNameRule {

    private static final String NAME_PREFIX = "NAME ";

    private DisplayNameRule() {
    }

    public static String choose(String mainId, List<String> identifiers) {
        Optional<String> messier = identifiers.stream()
                .map(String::trim)
                .filter(id -> Messier.DESIGNATION.matcher(id).matches())
                .map(Texts::collapseWhitespace)
                .sorted()
                .findFirst();
        if (messier.isPresent()) {
            return messier.get();
        }
        if (mainId != null && mainId.trim().startsWith(NAME_PREFIX)) {
            String main = Texts.collapseWhitespace(mainId.trim().substring(NAME_PREFIX.length()));
            if (!main.isEmpty()) {
                return main;
            }
        }
        Optional<String> commonName = identifiers.stream()
                .map(String::trim)
                .filter(id -> id.startsWith(NAME_PREFIX))
                .map(id -> Texts.collapseWhitespace(id.substring(NAME_PREFIX.length())))
                .filter(id -> !id.isEmpty())
                .min(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
        if (commonName.isPresent()) {
            return commonName.get();
        }
        if (mainId == null || mainId.isBlank()) {
            throw new IllegalArgumentException("main_id vazio e nenhum identificador utilizavel");
        }
        return Texts.collapseWhitespace(mainId);
    }
}
