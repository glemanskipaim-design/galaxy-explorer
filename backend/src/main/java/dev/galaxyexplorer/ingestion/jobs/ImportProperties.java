package dev.galaxyexplorer.ingestion.jobs;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param queueCapacity importacoes aguardando; acima disso a API responde 429 (e nada e gravado)
 * @param retention     por quanto tempo guardar jobs terminais e os payloads crus de importacoes
 *                      sob demanda que nao gravaram nenhum objeto (ver {@code ImportRetention}).
 *                      Minimo 1h: valor menor (ou zero/negativo, que apagaria jobs recem-terminados
 *                      a cada execucao) falha na inicializacao.
 */
@Validated
@ConfigurationProperties("galaxy.imports")
public record ImportProperties(@Min(1) @Max(1000) @DefaultValue("20") int queueCapacity,
                               @NotNull @DurationMin(hours = 1) @DefaultValue("30d") Duration retention) {
}
