package dev.galaxyexplorer.ingestion.connector;

/** Consulta que a fonte nao reconheceu, com o motivo legivel. */
public record UnresolvedQuery(String query, String reason) {
}
