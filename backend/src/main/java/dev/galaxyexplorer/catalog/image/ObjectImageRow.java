package dev.galaxyexplorer.catalog.image;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma linha de {@code object_image} + codigo/acknowledgment da fonte e nome da
 * licenca (via join). Campos opcionais podem ser {@code null}.
 */
public record ObjectImageRow(long id, UUID objectId, ImageKind kind, boolean hero, String sourceCode,
                             String sourceAcknowledgment, String sourceImageId, String imageUrl, String pageUrl,
                             String provider, String providerShort, String creditVerbatim, String mission,
                             String instrument, String wavelength, String title, String rightsText,
                             String licenseCode, String licenseName, String licenseUrl, String termsUrl,
                             LocalDate releaseDate, Double centerRaDeg, Double centerDecDeg, Double fovDeg,
                             String fovBasis, Double coverageRatio, Double separationArcmin, String matchBasis,
                             String curatedBy, boolean curatedHero, String contentSha256, int widthPx, int heightPx,
                             String thumbSha256, int thumbWidthPx, int thumbHeightPx, int galleryRank,
                             Instant retrievedAt) {

    public HeroCandidate toHeroCandidate() {
        return new HeroCandidate(id, kind, sourceCode, curatedHero, coverageRatio, releaseDate, separationArcmin);
    }
}
