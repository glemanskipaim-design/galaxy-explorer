package dev.galaxyexplorer.ingestion.images.curation;

import java.util.UUID;

import dev.galaxyexplorer.ingestion.connector.esa.EsaProvider;

/**
 * Uma linha valida de {@code images/curation.csv}, com o objeto ja resolvido no
 * catalogo local. A curadoria ESCOLHE imagens; os metadados sempre vem da AVM.
 *
 * @param note comentario livre do curador; pode ser vazio
 */
public record CurationEntry(int line, String objectQuery, UUID objectId, EsaProvider provider, String imageId,
                            Action action, String note) {

    public enum Action {
        /** Inclui a imagem (passa pelas regras, com {@code curated=true}). */
        INCLUDE,
        /** Inclui e manda ser o hero (so se o tipo puder ser hero). */
        HERO,
        /** Impede a selecao automatica de aceitar a imagem para o objeto. */
        EXCLUDE
    }
}
