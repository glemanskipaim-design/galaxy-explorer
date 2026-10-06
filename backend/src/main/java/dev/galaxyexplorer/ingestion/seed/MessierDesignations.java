package dev.galaxyexplorer.ingestion.seed;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Os 110 nomes do catalogo Messier como CONSULTAS ({@code "M1"} ... {@code "M110"}),
 * resolvidas pelo Sesame. Nao e dado astronomico: so a lista do que pedir.
 */
public final class MessierDesignations {

    public static final int COUNT = 110;

    private MessierDesignations() {
    }

    public static List<String> all() {
        return IntStream.rangeClosed(1, COUNT).mapToObj(n -> "M" + n).toList();
    }
}
