package dev.galaxyexplorer.catalog.position;

import java.time.Instant;

/** @param bibcode pode ser {@code null} */
public record PositionRow(double raDeg, double decDeg, String frame, String epoch, String sourceCode,
                          String bibcode, Instant retrievedAt) {
}
