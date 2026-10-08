package dev.galaxyexplorer.ingestion.connector.image;

import java.net.URI;

import org.springframework.http.MediaType;

import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.media.ImageFileStore;
import dev.galaxyexplorer.media.JpegInspector;

/**
 * Download de JPEG pelas regras de {@link GuardedHttpClient} + exige
 * {@code Content-Type: image/jpeg} E a assinatura {@code FF D8 FF} — as duas
 * coisas, porque cada uma sozinha pode mentir.
 */
public class HttpImageDownloader implements ImageDownloader {

    private final GuardedHttpClient http;
    private final long maxBytes;

    public HttpImageDownloader(GuardedHttpClient http, long maxBytes) {
        this.http = http;
        this.maxBytes = maxBytes;
    }

    @Override
    public DownloadedImage download(URI url, String service) {
        GuardedHttpClient.Response response = http.get(url, service, maxBytes);
        MediaType type = response.contentType();
        if (type == null || !MediaType.IMAGE_JPEG.equalsTypeAndSubtype(type)) {
            throw new SourceQueryException(service + ": esperado image/jpeg, recebido " + type);
        }
        if (!JpegInspector.hasJpegMagic(response.body())) {
            throw new SourceQueryException(service + ": Content-Type image/jpeg, mas o conteúdo não é JPEG");
        }
        return new DownloadedImage(response.body(), MediaType.IMAGE_JPEG_VALUE, response.finalUrl().toString(),
                ImageFileStore.sha256Hex(response.body()), response.retrievedAt());
    }
}
