package dev.galaxyexplorer.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Operacoes de texto usadas em mais de um pacote. Uma definicao so de cada. */
public final class Texts {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Texts() {
    }

    /** {@code '  M   31 '} -> {@code 'M 31'}. */
    public static String collapseWhitespace(String s) {
        return WHITESPACE.matcher(s.trim()).replaceAll(" ");
    }

    /** Remove todo whitespace ({@code 'm 31'} -> {@code 'm31'}). */
    public static String removeWhitespace(String s) {
        return WHITESPACE.matcher(s).replaceAll("");
    }

    /**
     * Percent-encoding de um componente de URL (query ou path), com espaco como
     * {@code %20} — o {@code URLEncoder} usa {@code +}, que nem todo servico decodifica.
     * Um {@code +} literal ja sai como {@code %2B} antes da troca.
     */
    public static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
