package dev.galaxyexplorer.ingestion.connector.image;

/** Imagem da fonte recusada para um objeto, com o motivo (vai para o relatorio e o error_summary). */
public record CandidateRejection(String sourceImageId, String reason) {
}
