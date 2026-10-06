package dev.galaxyexplorer.catalog.object;

import java.util.regex.Pattern;

/** Reconhecimento do catalogo Messier. Uma regex so para nome exibido e slug. */
public final class Messier {

    /**
     * {@code "M"} + espacos + 1 a 3 digitos ({@code 'M  31'}, {@code 'M 110'}). Exclui de
     * proposito outros catalogos com prefixo M, como Minkowski {@code 'M 1-67'}. Grupo 1 = numero.
     */
    public static final Pattern DESIGNATION = Pattern.compile("^M\\s+(\\d{1,3})$");

    private Messier() {
    }
}
