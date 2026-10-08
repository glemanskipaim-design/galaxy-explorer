package dev.galaxyexplorer.ingestion.connector.image;

import java.net.URI;

/**
 * Baixa um JPEG de uma fonte de imagem.
 *
 * @see HttpImageDownloader
 */
public interface ImageDownloader {

    /**
     * @param service nome da fonte para mensagens de erro ({@code "hips2fits"}, {@code "ESA"})
     * @throws dev.galaxyexplorer.ingestion.connector.SourceUnavailableException 429/5xx/rede/circuito aberto
     * @throws dev.galaxyexplorer.ingestion.connector.SourceQueryException       4xx, host fora da lista
     *         permitida, redirecionamento demais, nao-JPEG, grande demais
     */
    DownloadedImage download(URI url, String service);
}
