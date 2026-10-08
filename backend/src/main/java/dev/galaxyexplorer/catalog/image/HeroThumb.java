package dev.galaxyexplorer.catalog.image;

import java.util.UUID;

/**
 * Miniatura do hero de um objeto, para busca e listas.
 *
 * @param providerShort nome curto do provedor ({@code "ESA/Hubble"}, {@code "DSS2 via CDS"}); o credito
 *                      literal completo fica na pagina do objeto (decisao do usuario)
 */
public record HeroThumb(UUID objectId, String thumbSha256, int thumbWidthPx, int thumbHeightPx, ImageKind kind,
                        String providerShort, String licenseCode) {
}
