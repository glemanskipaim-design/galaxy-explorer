package dev.galaxyexplorer.ingestion.connector;

/**
 * @param bibcode        pode ser {@code null}
 * @param payloadDataset de qual {@link RawPayload} do lote a posicao veio
 */
public record NormalizedPosition(double raDeg, double decDeg, String frame, String epoch, String bibcode,
                                 String payloadDataset) {
}
