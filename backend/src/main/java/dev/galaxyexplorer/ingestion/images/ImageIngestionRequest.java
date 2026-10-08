package dev.galaxyexplorer.ingestion.images;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;

/**
 * @param objectIds vazio = todos os objetos do catalogo
 * @param refresh   {@code false}: objeto que ja tem imagem desta fonte nao e reconsultado
 */
public record ImageIngestionRequest(Set<ImageSource> sources, List<UUID> objectIds, RunTrigger trigger,
                                    boolean refresh) {

    public ImageIngestionRequest {
        sources = Set.copyOf(sources);
        objectIds = List.copyOf(objectIds);
    }
}
