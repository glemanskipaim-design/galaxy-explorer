package dev.galaxyexplorer.ingestion.connector.esa;

import java.time.LocalDate;
import java.util.List;

/**
 * Os campos da AVM (Astronomy Visualization Metadata) que usamos, ja
 * decodificados por {@link AvmValues}. Nomes {@code Spatial.*} como o JSON real
 * os grava. Campos opcionais podem ser {@code null}; listas nunca.
 *
 * @param scaleDegPerPx    {@code Spatial.Scale} (x, y) em graus/pixel do ORIGINAL; x vem negativo
 * @param dimensionPx      {@code Spatial.ReferenceDimension} (largura, altura) do original
 * @param referencePixel   {@code Spatial.ReferencePixel} (x, y)
 * @param date             {@code Date}: data de PUBLICACAO
 */
public record AvmRecord(String id, String type, String title, String credit, String rights,
                        List<String> subjectNames, List<String> facilities, List<String> instruments,
                        List<String> spectralBands, Double refRaDeg, Double refDecDeg, double[] scaleDegPerPx,
                        double[] dimensionPx, double[] referencePixel, String coordinateFrame, LocalDate date,
                        String referenceUrl) {

    public AvmRecord {
        subjectNames = List.copyOf(subjectNames);
        facilities = List.copyOf(facilities);
        instruments = List.copyOf(instruments);
        spectralBands = List.copyOf(spectralBands);
    }

    public boolean hasFrame() {
        return scaleDegPerPx != null && dimensionPx != null;
    }

    /** Largura do quadro em graus; so com {@link #hasFrame()}. */
    public double frameWidthDeg() {
        return Math.abs(scaleDegPerPx[0]) * dimensionPx[0];
    }

    public double frameHeightDeg() {
        return Math.abs(scaleDegPerPx[1]) * dimensionPx[1];
    }
}
