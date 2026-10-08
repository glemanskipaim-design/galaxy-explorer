package dev.galaxyexplorer.catalog.image;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import dev.galaxyexplorer.media.StoredFile;

/**
 * Uma linha de {@code object_image} a gravar. Credito, licenca e titulo vem
 * SEMPRE da fonte (AVM, HiPS {@code properties}) — este record so os carrega.
 * Campos sem anotacao de nulidade no V8 podem ser {@code null}.
 *
 * @param imageUrl  URL exata de onde os bytes foram baixados
 * @param curatedBy {@code automatic} ou {@code manual}
 */
public record ObjectImageWrite(ImageKind kind, String sourceImageId, long runId, Long metadataPayloadId,
                               long downloadPayloadId, String imageUrl, String pageUrl, String provider,
                               String providerShort, String creditVerbatim, String mission, String instrument,
                               String wavelength, String title, String rightsText, String licenseCode,
                               String licenseUrl, String termsUrl, LocalDate releaseDate, Double centerRaDeg,
                               Double centerDecDeg, Double fovDeg, String fovBasis, Double coverageRatio,
                               Double separationArcmin, Long derivedFromMeasurementId, String matchBasis,
                               String curatedBy, boolean curatedHero, StoredFile display, int widthPx,
                               int heightPx, StoredFile thumb, int thumbWidthPx, int thumbHeightPx,
                               Instant retrievedAt) {

    public ObjectImageWrite {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sourceImageId, "sourceImageId");
        Objects.requireNonNull(imageUrl, "imageUrl");
        Objects.requireNonNull(pageUrl, "pageUrl");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerShort, "providerShort");
        Objects.requireNonNull(creditVerbatim, "creditVerbatim");
        Objects.requireNonNull(rightsText, "rightsText");
        Objects.requireNonNull(licenseCode, "licenseCode");
        Objects.requireNonNull(matchBasis, "matchBasis");
        Objects.requireNonNull(curatedBy, "curatedBy");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(thumb, "thumb");
        Objects.requireNonNull(retrievedAt, "retrievedAt");
        if (curatedHero && !kind.canBeHero()) {
            throw new IllegalArgumentException(kind + " nunca pode ser hero");
        }
    }
}
