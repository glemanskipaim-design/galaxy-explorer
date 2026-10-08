package dev.galaxyexplorer.api.image;

import dev.galaxyexplorer.catalog.image.HeroThumb;
import dev.galaxyexplorer.catalog.image.ImageKind;
import dev.galaxyexplorer.catalog.image.ObjectImageRow;

/** Linhas de imagem -> contrato JSON. Puro. */
public final class ImageDtoMapper {

    public static final String FILES_PATH = "/api/v1/images/files/";

    private ImageDtoMapper() {
    }

    /** URL relativa, mesma origem, de um arquivo servido por {@link ImageFileController}. */
    public static String fileUrl(String sha256) {
        return FILES_PATH + sha256 + ".jpg";
    }

    public static ImageDto image(ObjectImageRow r) {
        ImageDto.Cutout cutout = r.kind() != ImageKind.SURVEY_CUTOUT ? null
                : new ImageDto.Cutout(r.mission(), r.sourceImageId(), r.fovDeg(), r.fovBasis(), r.centerRaDeg(),
                        r.centerDecDeg());
        return new ImageDto(r.id(), r.kind().dbValue(), r.hero(), fileUrl(r.contentSha256()), r.widthPx(),
                r.heightPx(), new ImageDto.Thumb(fileUrl(r.thumbSha256()), r.thumbWidthPx(), r.thumbHeightPx()),
                r.title(), r.provider(), r.providerShort(), r.sourceCode(), r.creditVerbatim(),
                r.sourceAcknowledgment(), new ImageDto.License(r.licenseCode(), r.licenseName(), r.licenseUrl()),
                r.rightsText(), r.termsUrl(), r.mission(), r.instrument(), r.wavelength(), r.pageUrl(),
                r.sourceImageId(), r.releaseDate(), cutout, r.coverageRatio(), r.matchBasis(), r.curatedBy(),
                r.retrievedAt());
    }

    /** @return {@code null} quando o objeto nao tem hero */
    public static ThumbnailDto thumbnail(HeroThumb t) {
        if (t == null) {
            return null;
        }
        return new ThumbnailDto(fileUrl(t.thumbSha256()), t.thumbWidthPx(), t.thumbHeightPx(), t.kind().dbValue(),
                t.providerShort(), t.licenseCode());
    }
}
