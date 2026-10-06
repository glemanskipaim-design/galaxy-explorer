package dev.galaxyexplorer.ingestion.connector.simbad;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import dev.galaxyexplorer.common.Texts;

/**
 * Monta a URL do Sesame: {@code <base>?nome1&nome2...}, cada nome
 * percent-encoded (espaco como {@code %20}). Puro.
 */
public final class SesameUrls {

    private SesameUrls() {
    }

    public static URI build(URI base, List<String> names) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("nenhum nome para resolver");
        }
        String query = names.stream()
                .map(Texts::urlEncode)
                .collect(Collectors.joining("&"));
        return URI.create(base.toString() + "?" + query);
    }
}
