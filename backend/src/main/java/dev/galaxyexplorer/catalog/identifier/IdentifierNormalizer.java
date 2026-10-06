package dev.galaxyexplorer.catalog.identifier;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.galaxyexplorer.common.Texts;

/**
 * Unica normalizacao de identificadores do sistema. Usada na gravacao
 * ({@code CatalogWriter}) e na busca ({@code SearchService}) — e isso que faz
 * {@code M31} achar {@code M  31}.
 *
 * <p>Limitacao consciente: nao remove zeros a esquerda ({@code M031} != {@code M31}).
 */
public final class IdentifierNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    /** Prefixos de convencao do SIMBAD: {@code NAME Betelgeuse}, {@code * alf Ori}, {@code V* alf Ori}. */
    private static final List<String> SIMBAD_PREFIXES = List.of("name ", "* ", "v* ");

    private IdentifierNormalizer() {
    }

    /**
     * @throws IllegalArgumentException se {@code raw} for nulo ou em branco
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("identificador nulo ou em branco");
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKD);
        s = DIACRITICS.matcher(s).replaceAll("");
        s = s.toLowerCase(Locale.ROOT).trim();
        for (String prefix : SIMBAD_PREFIXES) {
            if (s.startsWith(prefix)) {
                s = s.substring(prefix.length());
                break;
            }
        }
        s = Texts.removeWhitespace(s);
        if (s.isEmpty()) {
            throw new IllegalArgumentException("identificador vazio após normalização: '" + raw + "'");
        }
        return s;
    }

    /**
     * Catalogo do identificador: o primeiro token antes do primeiro espaco
     * ({@code 'M  31'} -> {@code 'M'}). Identificador sem espaco -> {@code null}.
     */
    public static String catalogOf(String identifier) {
        if (identifier == null) {
            return null;
        }
        String trimmed = identifier.trim();
        int space = trimmed.indexOf(' ');
        return space <= 0 ? null : trimmed.substring(0, space);
    }
}
