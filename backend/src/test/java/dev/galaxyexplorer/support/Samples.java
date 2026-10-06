package dev.galaxyexplorer.support;

import java.util.List;

/** Conjunto de amostra coberto pelas fixtures reais (ver fixtures/README.md). */
public final class Samples {

    /** Exatamente fixtures/sesame/sample_resolve.names: 11 objetos reais + 1 nome inexistente. */
    public static final List<String> QUERIES = List.of("M31", "M1", "M42", "M45", "M87", "M13", "M57", "M82",
            "Betelgeuse", "Sgr A*", "M54", "XyzzyNotAnObject123");

    public static final String NOT_FOUND_QUERY = "XyzzyNotAnObject123";

    private Samples() {
    }
}
