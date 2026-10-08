package dev.galaxyexplorer.ingestion.connector.hips2fits;

/**
 * O recorte a pedir para um objeto.
 *
 * @param fovDeg                   maior lado do recorte (semantica do {@code fov} do hips2fits)
 * @param fovBasis                 de onde veio o campo, em portugues, com os numeros
 * @param derivedFromMeasurementId medicao de tamanho angular usada; pode ser {@code null}
 */
public record CutoutPlan(HipsSurvey survey, double raDeg, double decDeg, double fovDeg, String fovBasis,
                         Long derivedFromMeasurementId, int width, int height) {
}
