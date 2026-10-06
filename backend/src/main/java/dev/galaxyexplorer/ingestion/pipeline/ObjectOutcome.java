package dev.galaxyexplorer.ingestion.pipeline;

import java.util.Objects;
import java.util.UUID;

/**
 * O que aconteceu com uma consulta do pedido.
 *
 * @param kind          categoria do desfecho (o que vai para o usuario e por categoria; o detalhe so para
 *                      log e {@code error_summary})
 * @param objectId      {@code null} se nao foi gravado
 * @param slug          {@code null} se nao foi gravado
 * @param failureReason detalhe tecnico; {@code null} se deu certo
 */
public record ObjectOutcome(String query, Kind kind, UUID objectId, String slug, boolean created,
                            String failureReason) {

    public enum Kind {
        WRITTEN,
        /** a fonte nao reconheceu o nome (nao e erro) */
        NOT_FOUND,
        SOURCE_UNAVAILABLE,
        SOURCE_QUERY_FAILED,
        SCHEMA_MISMATCH,
        REJECTED,
        WRITE_FAILED,
        INTERNAL_ERROR
    }

    public ObjectOutcome {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(kind, "kind");
    }

    public static ObjectOutcome written(String query, UUID objectId, String slug, boolean created) {
        return new ObjectOutcome(query, Kind.WRITTEN, objectId, slug, created, null);
    }

    public static ObjectOutcome notFound(String query, String reason) {
        return new ObjectOutcome(query, Kind.NOT_FOUND, null, null, false, reason);
    }

    public static ObjectOutcome failed(String query, Kind kind, String reason) {
        if (kind == Kind.WRITTEN || kind == Kind.NOT_FOUND) {
            throw new IllegalArgumentException("categoria de falha esperada, veio " + kind);
        }
        return new ObjectOutcome(query, kind, null, null, false, reason);
    }

    public boolean ok() {
        return kind == Kind.WRITTEN;
    }

    public boolean notFound() {
        return kind == Kind.NOT_FOUND;
    }
}
