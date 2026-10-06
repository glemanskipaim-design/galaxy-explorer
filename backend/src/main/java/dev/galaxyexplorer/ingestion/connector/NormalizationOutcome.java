package dev.galaxyexplorer.ingestion.connector;

/** Resultado da normalizacao de um objeto: aceito ou rejeitado com motivo. */
public sealed interface NormalizationOutcome {

    record Accepted(NormalizedObject object) implements NormalizationOutcome {
    }

    record Rejected(ResolvedName name, String reason) implements NormalizationOutcome {
    }
}
