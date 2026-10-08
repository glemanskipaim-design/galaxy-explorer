package dev.galaxyexplorer.api.image;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Imagem completa de um objeto (contrato JSON). {@code url}/{@code thumbnail.url} sao RELATIVAS a mesma
 * origem ({@code /api/v1/images/files/<sha>.jpg}): o navegador nunca recebe URL de terceiro como imagem.
 *
 * @param credit         credito literal da fonte (AVM Credit / HiPS obs_copyright)
 * @param acknowledgment agradecimento exigido pela fonte do servico (hips2fits); pode ser {@code null}
 * @param releaseDate    data de PUBLICACAO (ESA); pode ser {@code null}
 * @param cutout         detalhes do recorte; {@code null} para imagem que nao e recorte
 * @param coverageRatio  menor lado do quadro / tamanho angular do objeto; pode ser {@code null}
 */
public record ImageDto(long id, String kind, boolean hero, String url, int width, int height, Thumb thumbnail,
                       String title, String provider, String providerShort, String source, String credit,
                       String acknowledgment, License license, String rightsText, String termsUrl, String mission,
                       String instrument, String wavelength, String pageUrl, String sourceImageId,
                       LocalDate releaseDate, Cutout cutout, Double coverageRatio, String matchBasis,
                       String curatedBy, Instant retrievedAt) {

    public record Thumb(String url, int width, int height) {
    }

    public record License(String code, String name, String url) {
    }

    public record Cutout(String survey, String hipsId, Double fovDeg, String fovBasis, Double centerRaDeg,
                         Double centerDecDeg) {
    }
}
