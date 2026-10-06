package dev.galaxyexplorer.ingestion.connector.simbad;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuracao do acesso ao CDS (SIMBAD TAP + resolvedor Sesame).
 *
 * @param requestsPerSecond teto somando Sesame e TAP (um limitador so). O CDS
 *                          bloqueia o IP por 1 min acima de 6 req/s.
 */
@Validated
@ConfigurationProperties("galaxy.simbad")
public record SimbadProperties(
        @NotNull @DefaultValue("https://simbad.cds.unistra.fr/simbad/sim-tap/sync") URI tapUrl,
        @NotNull @DefaultValue("https://cds.unistra.fr/cgi-bin/nph-sesame/-oxp/S") URI sesameUrl,
        @Min(1) @Max(5) @DefaultValue("2") int requestsPerSecond,
        @NotNull @DefaultValue("10s") Duration connectTimeout,
        @NotNull @DefaultValue("60s") Duration readTimeout,
        @Min(1) @DefaultValue("100000") int maxRec,
        @Min(1) @Max(10) @DefaultValue("3") int retryMaxAttempts,
        @NotNull @DefaultValue("1s") Duration retryInitialBackoff,
        @NotBlank @DefaultValue("GalaxyExplorer/0.1 (personal research project)") String userAgent) {
}
