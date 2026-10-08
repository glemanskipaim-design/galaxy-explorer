package dev.galaxyexplorer.ingestion.connector.hips2fits;

import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.image.DownloadedImage;

/** Acesso ao CDS para recortes: o {@code properties} do survey e o recorte em si. */
public interface Hips2FitsClient {

    String DATASET_PROPERTIES = "hips_properties";
    String DATASET_CUTOUT = "hips2fits_cutout";

    /** Texto exato do arquivo {@code properties} (dataset {@value #DATASET_PROPERTIES}). */
    RawPayload properties(HipsSurvey survey);

    /** JPEG do recorte; {@link DownloadedImage#requestUrl()} e a URL efetivamente usada. */
    DownloadedImage cutout(CutoutPlan plan);
}
