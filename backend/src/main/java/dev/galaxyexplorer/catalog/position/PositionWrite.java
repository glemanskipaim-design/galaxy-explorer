package dev.galaxyexplorer.catalog.position;

import java.time.Instant;

/**
 * Posicao a gravar. {@code gal_l/gal_b/constellation} ficam NULL no Bloco 1
 * (sao "calculados por nos" e entram depois, marcados como tal).
 *
 * @param posErrMas pode ser {@code null}
 * @param bibcode   pode ser {@code null}; o {@code CatalogWriter} resolve para {@code reference_id}
 */
public record PositionWrite(double raDeg, double decDeg, String frame, String epoch, Double posErrMas,
                            String bibcode, Instant retrievedAt) {
}
