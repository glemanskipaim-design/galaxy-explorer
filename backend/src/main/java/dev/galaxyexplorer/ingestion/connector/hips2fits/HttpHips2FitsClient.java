package dev.galaxyexplorer.ingestion.connector.hips2fits;

import java.net.URI;
import java.util.List;

import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import dev.galaxyexplorer.ingestion.connector.image.DownloadedImage;
import dev.galaxyexplorer.ingestion.connector.image.GuardedHttpClient;
import dev.galaxyexplorer.ingestion.connector.image.ImageDownloader;

/**
 * hips2fits via HTTP. As URLs base sao tentadas em ordem, e a seguinte so em
 * {@link SourceUnavailableException} (servidor fora/sobrecarregado) — um 4xx e
 * erro do pedido e repetir no espelho daria o mesmo erro.
 */
public class HttpHips2FitsClient implements Hips2FitsClient {

    static final long MAX_PROPERTIES_BYTES = 1024 * 1024;
    private static final String SERVICE = "hips2fits";

    private final GuardedHttpClient http;
    private final ImageDownloader downloader;
    private final List<URI> baseUrls;

    public HttpHips2FitsClient(GuardedHttpClient http, ImageDownloader downloader, List<URI> baseUrls) {
        if (baseUrls.isEmpty()) {
            throw new IllegalArgumentException("nenhuma URL base do hips2fits configurada");
        }
        this.http = http;
        this.downloader = downloader;
        this.baseUrls = List.copyOf(baseUrls);
    }

    @Override
    public RawPayload properties(HipsSurvey survey) {
        GuardedHttpClient.Response r = http.get(survey.propertiesUrl(), "HiPS properties", MAX_PROPERTIES_BYTES);
        return RawPayload.of(DATASET_PROPERTIES, r.finalUrl().toString(), survey.hipsId(), r.text(),
                RawPayload.BodyFormat.TEXT, r.retrievedAt());
    }

    @Override
    public DownloadedImage cutout(CutoutPlan plan) {
        SourceUnavailableException last = null;
        for (URI base : baseUrls) {
            try {
                return downloader.download(Hips2FitsUrls.cutout(base, plan), SERVICE);
            } catch (SourceUnavailableException e) {
                last = e;
            }
        }
        throw last;
    }
}
