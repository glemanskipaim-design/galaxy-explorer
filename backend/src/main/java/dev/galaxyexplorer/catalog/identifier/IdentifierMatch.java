package dev.galaxyexplorer.catalog.identifier;

import java.util.UUID;

/** Um identificador que casou com uma busca; {@code score} = 1.0 para igualdade exata. */
public record IdentifierMatch(UUID objectId, String identifier, String normalized, double score) {
}
