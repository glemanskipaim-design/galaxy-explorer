package dev.galaxyexplorer.ingestion.connector.image;

import java.time.Instant;

/**
 * Bytes de imagem recebidos, ja conferidos como JPEG (Content-Type + assinatura).
 *
 * @param requestUrl URL que efetivamente respondeu os bytes (depois de redirecionamentos)
 * @param sha256     hex do sha256 de {@code bytes}
 */
public record DownloadedImage(byte[] bytes, String contentType, String requestUrl, String sha256,
                              Instant retrievedAt) {
}
