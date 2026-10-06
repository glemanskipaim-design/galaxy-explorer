package dev.galaxyexplorer.ingestion.pipeline;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Fica no pipeline (generico), nao no conector: o pipeline nao importa nada de
 * uma fonte especifica.
 *
 * @param batchSize consultas por lote (cada lote = 1 resolucao + N consultas de dados)
 */
@Validated
@ConfigurationProperties("galaxy.ingestion")
public record IngestionProperties(@Min(1) @Max(50) @DefaultValue("10") int batchSize) {
}
