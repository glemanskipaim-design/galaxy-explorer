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

    /**
     * Registro de um download binario (imagem). {@code raw_payload.body} e jsonb: os bytes nao cabem
     * ali — o artefato cru e o ARQUIVO em disco, enderecado por sha256. O corpo guarda a referencia
     * ({@code contentType}, {@code byteLength}, {@code storageKey}) e {@code sha256} e o dos BYTES da
     * imagem (o mesmo de {@code object_image.content_sha256}), nao o do JSON de referencia.
     *
     * @param contentType   {@code Content-Type} recebido
     * @param byteLength    tamanho dos bytes recebidos
     * @param contentSha256 sha256 hex dos bytes recebidos
     * @param storageKey    chave do arquivo gravado ({@code ab/<sha>.jpg})
     */
    public static RawPayload ofBinary(String dataset, String requestUrl, String queryText, String contentType,
                                      long byteLength, String contentSha256, String storageKey,
                                      Instant retrievedAt) {
        String body = "{\"contentType\":" + jsonString(contentType) + ",\"byteLength\":" + byteLength
                + ",\"storageKey\":" + jsonString(storageKey) + "}";
        return new RawPayload(dataset, requestUrl, queryText, body, BodyFormat.JSON, contentSha256, retrievedAt);
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append('"').toString();
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
