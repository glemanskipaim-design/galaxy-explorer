package dev.galaxyexplorer.ingestion.connector.esa;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.galaxyexplorer.catalog.identifier.IdentifierNormalizer;

/**
 * A imagem e deste objeto pelo NOME? Compara AVM {@code Subject.Name} com os
 * identificadores do objeto usando o MESMO {@link IdentifierNormalizer} do
 * catalogo, mais uma unica regra de alias: {@code Messier N} -> {@code M N} (a
 * AVM grava "Messier 51", o SIMBAD "M  51"). "Whirlpool Galaxy" casa com
 * "NAME Whirlpool Galaxy" pela normalizacao normal. Puro.
 */
public final class SubjectNameMatcher {

    private static final Pattern MESSIER_LONG = Pattern.compile("^Messier\\s+(\\d{1,3})$", Pattern.CASE_INSENSITIVE);

    private SubjectNameMatcher() {
    }

    /** @return explicacao do casamento ("AVM Subject.Name 'Messier 51' = identificador 'M  51'") ou vazio */
    public static Optional<String> match(List<String> subjectNames, List<String> objectIdentifiers) {
        Map<String, String> byNormalized = new LinkedHashMap<>();
        for (String identifier : objectIdentifiers) {
            normalize(identifier).ifPresent(n -> byNormalized.putIfAbsent(n, identifier));
        }
        for (String subject : subjectNames) {
            Optional<String> key = normalize(alias(subject.trim()));
            if (key.isPresent() && byNormalized.containsKey(key.get())) {
                return Optional.of("AVM Subject.Name '" + subject + "' = identificador '" + byNormalized.get(key.get())
                        + "'");
            }
        }
        return Optional.empty();
    }

    /** Designacao Messier por extenso, como a busca da ESA indexa ({@code M  51} -> {@code Messier 51}). */
    public static String longMessier(int number) {
        return "Messier " + number;
    }

    static String alias(String subject) {
        Matcher m = MESSIER_LONG.matcher(subject);
        return m.matches() ? "M " + Integer.parseInt(m.group(1)) : subject;
    }

    private static Optional<String> normalize(String s) {
        try {
            return Optional.of(IdentifierNormalizer.normalize(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
