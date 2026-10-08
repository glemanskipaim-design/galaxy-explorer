package dev.galaxyexplorer.ingestion.images;

import java.util.UUID;

/**
 * Desfecho de uma fonte de imagem para um objeto.
 *
 * @param detail motivo/explicacao (vai para o relatorio; falhas tambem para o error_summary)
 */
public record ImageOutcome(UUID objectId, String slug, ImageSource source, Kind kind, String detail) {

    public enum Kind {
        /** Uma ou mais imagens gravadas/atualizadas. */
        STORED,
        /** Ja havia imagem desta fonte e {@code refresh=false}: nenhuma requisicao. */
        UNCHANGED,
        NO_POSITION,
        /** Nenhuma candidata passou nas regras (motivos no detalhe). */
        NO_VALID_CANDIDATE,
        SOURCE_UNAVAILABLE,
        SOURCE_QUERY_FAILED,
        WRITE_FAILED;

        public boolean failure() {
            return this == SOURCE_UNAVAILABLE || this == SOURCE_QUERY_FAILED || this == WRITE_FAILED;
        }
    }
}
