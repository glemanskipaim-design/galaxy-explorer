package dev.galaxyexplorer.ingestion.connector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Resposta crua de uma fonte, exatamente como recebida. Permite auditar e
 * re-normalizar sem reconsultar a fonte.
 *
 * @param queryText consulta exata (ADQL, lista de nomes do Sesame); pode ser {@code null}
 * @param body      texto exato recebido
 * @param format    {@code JSON} vai para {@code raw_payload.body} como documento jsonb;
 *                  {@code TEXT} (ex. XML do Sesame) vai como string jsonb
 * @param sha256    hex do SHA-256 dos bytes UTF-8 de {@code body}
 */
public record RawPayload(String dataset, String requestUrl, String queryText, String body, BodyFormat format,
                         String sha256, Instant retrievedAt) {

    public enum BodyFormat {
        JSON,
        TEXT
    }

    public RawPayload {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(requestUrl, "requestUrl");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(retrievedAt, "retrievedAt");
    }

    public static RawPayload of(String dataset, String requestUrl, String queryText, String body, BodyFormat format,
                                Instant retrievedAt) {
        return new RawPayload(dataset, requestUrl, queryText, body, format, sha256Hex(body), retrievedAt);
    }

    public static String sha256Hex(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }
}
