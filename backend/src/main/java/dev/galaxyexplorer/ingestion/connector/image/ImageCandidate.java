package dev.galaxyexplorer.ingestion.connector.image;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.catalog.image.ImageKind;

/**
 * Imagem aceita para um objeto, pronta para baixar e gravar (modelo canonico de
 * saida dos conectores de imagem). Todo texto de credito/licenca/titulo veio da
 * fonte. Campos opcionais podem ser {@code null}.
 *
 * @param providerShort    rotulo curto para miniaturas
 * @param coverageRatio    menor lado do quadro / tamanho angular do objeto ({@code null} = desconhecido)
 * @param separationArcmin centro da imagem ate a posicao do objeto ({@code null} = nao calculada)
 * @param matchBasis       por que esta imagem e deste objeto, com os numeros usados
 * @param downloadUrl      de onde baixar os bytes exibidos
 * @param curated          veio da curadoria manual
 */
public record ImageCandidate(SourceCode source, String sourceImageId, ImageKind kind, String title, String provider,
                             String providerShort, String creditVerbatim, String rightsText, String licenseCode,
                             String termsUrl, String mission, String instrument, String wavelength, String pageUrl,
                             LocalDate releaseDate, Double centerRaDeg, Double centerDecDeg, Double fovDeg,
                             String fovBasis, Double coverageRatio, Double separationArcmin,
                             Long derivedFromMeasurementId, String matchBasis, URI downloadUrl, boolean curated,
                             boolean curatedHero) {

    public ImageCandidate {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceImageId, "sourceImageId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerShort, "providerShort");
        Objects.requireNonNull(creditVerbatim, "creditVerbatim");
        Objects.requireNonNull(rightsText, "rightsText");
        Objects.requireNonNull(licenseCode, "licenseCode");
        Objects.requireNonNull(pageUrl, "pageUrl");
        Objects.requireNonNull(matchBasis, "matchBasis");
        Objects.requireNonNull(downloadUrl, "downloadUrl");
    }
}
