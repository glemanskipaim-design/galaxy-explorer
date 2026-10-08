package dev.galaxyexplorer.ingestion.connector.hips2fits;

import java.net.URI;

/**
 * Um survey HiPS que pode gerar recortes (configuracao, {@code galaxy.images.hips2fits.surveys}).
 *
 * @param code          nosso codigo ({@code DSS2_COLOR})
 * @param hipsId        id do HiPS pedido ao hips2fits ({@code CDS/P/DSS2/color})
 * @param propertiesUrl arquivo {@code properties} de onde vem credito e licenca
 * @param shortLabel    rotulo curto para miniaturas ({@code "DSS2 via CDS"}) — nosso, nao da fonte
 * @param minDecDeg     cobertura: borda inferior do recorte nao pode ficar abaixo disto
 * @param maxFovDeg     maior campo em que o survey ainda e usado
 */
public record HipsSurvey(String code, String hipsId, URI propertiesUrl, String shortLabel, double minDecDeg,
                         double maxFovDeg) {
}
