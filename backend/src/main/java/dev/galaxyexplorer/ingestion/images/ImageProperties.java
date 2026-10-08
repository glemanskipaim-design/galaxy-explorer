package dev.galaxyexplorer.ingestion.images;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Configuracao da ingestao de imagens ({@code galaxy.images}, valores em
 * {@code application.yml}). Limitadores SEPARADOS do limitador do SIMBAD: o
 * hips2fits e outro servico (pesado) e a ESA e outra organizacao.
 *
 * @param allowedHosts unicos hosts com que a ingestao de imagens fala (exatos, https)
 * @param curationFile recurso do classpath com a curadoria manual
 */
@Validated
@ConfigurationProperties("galaxy.images")
public record ImageProperties(
        @NotBlank String userAgent,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(1) @Max(10) int retryMaxAttempts,
        @NotNull Duration retryInitialBackoff,
        @NotEmpty List<String> allowedHosts,
        @NotBlank String curationFile,
        @Valid @NotNull Hips2Fits hips2fits,
        @Valid @NotNull Esa esa) {

    /**
     * @param baseUrls servico principal e espelho (o espelho so e tentado com o principal indisponivel)
     * @param surveys  ordem = preferencia
     */
    public record Hips2Fits(@NotEmpty List<URI> baseUrls, @Min(1) @Max(5) int requestsPerSecond,
                            @Min(16) @Max(4000) int width, @Min(16) @Max(4000) int height,
                            @DecimalMin("0.1") double fovFactor, @DecimalMin("0.001") double fovMinDeg,
                            @DecimalMin("0.001") double fovMaxDeg, @DecimalMin("0.001") double fovDefaultDeg,
                            @Valid @NotEmpty List<Survey> surveys) {
    }

    public record Survey(@NotBlank String code, @NotBlank String hipsId, @NotNull URI propertiesUrl,
                         @NotBlank String shortLabel, double minDecDeg, double maxFovDeg) {
    }

    /** @param requestsPerSecond um limitador so para esahubble, esawebb e as CDNs (mesma organizacao) */
    public record Esa(@Min(1) @Max(5) int requestsPerSecond, @Min(1) @Max(50) int maxCandidatesPerSearch,
                      @Min(1) @Max(10) int maxImagesPerObject, @Valid @NotNull Site hubble,
                      @Valid @NotNull Site webb) {
    }

    public record Site(@NotNull URI baseUrl, @NotNull URI cdnUrl) {
    }
}
