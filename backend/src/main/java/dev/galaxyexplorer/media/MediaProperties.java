package dev.galaxyexplorer.media;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Arquivos de imagem hospedados pelo backend.
 *
 * @param storageDir    raiz dos arquivos (relativo ao diretorio de trabalho do backend; em Docker, volume)
 * @param maxImageBytes download maior que isto e rejeitado
 * @param maxPixels     largura x altura maxima, conferida no CABECALHO antes de decodificar
 *                      (protecao contra "bomba de descompressao")
 * @param thumbMaxSize  maior lado da miniatura, em px
 */
@Validated
@ConfigurationProperties("galaxy.media")
public record MediaProperties(
        @NotNull @DefaultValue("./data/images") Path storageDir,
        @Min(1024) @Max(67108864) @DefaultValue("8388608") long maxImageBytes,
        @Min(1) @DefaultValue("40000000") long maxPixels,
        @Min(32) @Max(1024) @DefaultValue("320") int thumbMaxSize) {
}
