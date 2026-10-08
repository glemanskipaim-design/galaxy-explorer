package dev.galaxyexplorer.api.image;

/**
 * Miniatura do hero, para busca e listas.
 *
 * @param provider nome curto do provedor ({@code "ESA/Hubble"}, {@code "DSS2 via CDS"}); o credito literal
 *                 completo aparece junto da imagem na pagina do objeto (decisao do usuario)
 */
public record ThumbnailDto(String url, int width, int height, String kind, String provider, String licenseCode) {
}
