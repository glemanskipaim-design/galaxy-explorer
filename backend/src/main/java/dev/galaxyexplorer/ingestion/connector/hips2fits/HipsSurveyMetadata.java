package dev.galaxyexplorer.ingestion.connector.hips2fits;

/**
 * Credito e licenca de um survey, literais do arquivo {@code properties}.
 *
 * @param obsCopyrightUrl pode ser {@code null}
 * @param hipsCopyright   titular da licenca {@code hips_license} do HiPS (ex. {@code CNRS/Unistra}); diferente de
 *                        {@code obs_copyright}, que e o credito dos dados; pode ser {@code null}
 * @param bibReference    pode ser {@code null}
 * @param creatorDid      pode ser {@code null}
 */
public record HipsSurveyMetadata(String hipsId, String obsTitle, String obsCopyright, String obsCopyrightUrl,
                                 String hipsLicense, String hipsCopyright, String bibReference,
                                 String creatorDid) {
}
