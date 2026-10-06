package dev.galaxyexplorer.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import dev.galaxyexplorer.ingestion.connector.RawPayload;

/**
 * Le as fixtures REAIS de {@code src/test/resources/fixtures/} (capturadas ao
 * vivo; ver {@code fixtures/README.md}). Nunca gera dado.
 */
public final class Fixtures {

    /** Momento da captura das fixtures (UTC), usado como {@code retrieved_at} dos payloads de teste. */
    public static final Instant CAPTURED_AT = Instant.parse("2026-10-05T22:20:00Z");

    public static final String SIMBAD_TAP_URL = "https://simbad.cds.unistra.fr/simbad/sim-tap/sync";

    private Fixtures() {
    }

    /** @param name caminho relativo a {@code fixtures/}, ex. {@code "simbad/sample_basic.json"} */
    public static String read(String name) {
        String path = "/fixtures/" + name;
        try (InputStream in = Fixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("fixture inexistente: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Payload TAP do SIMBAD a partir do par {@code simbad/<name>.adql} + {@code simbad/<name>.json}. */
    public static RawPayload payload(String dataset, String name) {
        return RawPayload.of(dataset, SIMBAD_TAP_URL, read("simbad/" + name + ".adql").trim(),
                read("simbad/" + name + ".json"), RawPayload.BodyFormat.JSON, CAPTURED_AT);
    }
}
