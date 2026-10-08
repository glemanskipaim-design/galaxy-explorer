package dev.galaxyexplorer.ingestion.connector.esa;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Enderecos de um site ESA (configuracao). Formas verificadas ao vivo em
 * 2026-10-07 nos dois sites:
 * <ul>
 *   <li>busca: {@code <base>/images/archive/search/?adv=&subject_name=Messier+51};</li>
 *   <li>AVM: {@code <base>/images/<id>/api/json/};</li>
 *   <li>JPG "screen" (1280 px de largura): {@code <cdn>/archives/images/screen/<id>.jpg}.</li>
 * </ul>
 */
public record EsaSite(EsaProvider provider, URI baseUrl, URI cdnUrl) {

    /** Ids de imagem da ESA: minusculas e digitos ({@code heic0506a}, {@code opo9622a1}). */
    public static final Pattern IMAGE_ID = Pattern.compile("^[a-z0-9]{3,40}$");

    public EsaSite {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(cdnUrl, "cdnUrl");
    }

    public URI searchUrl(String subjectTerm) {
        return URI.create(stripSlash(baseUrl) + "/images/archive/search/?adv=&subject_name="
                + URLEncoder.encode(subjectTerm, StandardCharsets.UTF_8));
    }

    public URI avmUrl(String imageId) {
        return URI.create(stripSlash(baseUrl) + "/images/" + checked(imageId) + "/api/json/");
    }

    public URI screenUrl(String imageId) {
        return URI.create(stripSlash(cdnUrl) + "/archives/images/screen/" + checked(imageId) + ".jpg");
    }

    private static String checked(String imageId) {
        if (imageId == null || !IMAGE_ID.matcher(imageId).matches()) {
            throw new IllegalArgumentException("id de imagem ESA inválido: " + imageId);
        }
        return imageId;
    }

    private static String stripSlash(URI uri) {
        String s = uri.toString();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
