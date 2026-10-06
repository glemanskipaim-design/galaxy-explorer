package dev.galaxyexplorer.catalog.object;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gera o slug de URL de um objeto. O slug e definido na criacao e nunca muda
 * (URL estavel), mesmo que o display name mude depois.
 */
public final class SlugFactory {

    public static final int MAX_LENGTH = 120;

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private SlugFactory() {
    }

    /** {@code 'M 31'} -> {@code 'messier-31'}; {@code 'Sgr A*'} -> {@code 'sgr-a'}. */
    public static String baseSlug(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display name vazio");
        }
        String name = displayName.trim();
        Matcher messier = Messier.DESIGNATION.matcher(name);
        if (messier.matches()) {
            name = "messier " + messier.group(1);
        }
        String s = Normalizer.normalize(name, Normalizer.Form.NFKD);
        s = DIACRITICS.matcher(s).replaceAll("").toLowerCase(Locale.ROOT);
        s = NON_ALNUM.matcher(s).replaceAll("-");
        s = trimHyphens(s);
        if (s.length() > MAX_LENGTH) {
            s = trimHyphens(s.substring(0, MAX_LENGTH));
        }
        return s.isEmpty() ? "object" : s;
    }

    /** {@code withSuffix("messier-31", 2)} -> {@code "messier-31-2"}, respeitando o tamanho maximo. */
    public static String withSuffix(String base, int n) {
        String suffix = "-" + n;
        String head = base.length() + suffix.length() > MAX_LENGTH
                ? trimHyphens(base.substring(0, MAX_LENGTH - suffix.length()))
                : base;
        return head + suffix;
    }

    private static String trimHyphens(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '-') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '-') {
            end--;
        }
        return s.substring(start, end);
    }
}
