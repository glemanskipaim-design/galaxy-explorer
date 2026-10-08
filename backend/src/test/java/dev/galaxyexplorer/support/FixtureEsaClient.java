package dev.galaxyexplorer.support;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.esa.AvmJsonParser;
import dev.galaxyexplorer.ingestion.connector.esa.EsaOutreachClient;
import dev.galaxyexplorer.ingestion.connector.esa.EsaProvider;
import dev.galaxyexplorer.ingestion.connector.esa.EsaSite;
import dev.galaxyexplorer.ingestion.connector.image.DownloadedImage;
import dev.galaxyexplorer.ingestion.connector.image.ImageDownloader;
import dev.galaxyexplorer.media.ImageFileStore;

/**
 * Busca, AVM e download ESA de teste: so respostas REAIS de {@code fixtures/esa/}. Pedido sem fixture =
 * {@link SourceQueryException} (efeito de um 4xx real), nunca resposta inventada.
 * <ul>
 *   <li>busca: {@code <hubble|webb>_search_<termo>.html} (termo em minusculas, espaco = {@code _});</li>
 *   <li>AVM: {@code <hubble|webb>_avm_<id>.json};</li>
 *   <li>download: {@code <hubble|webb>_screen_<id>.jpg} pela URL "screen" exata.</li>
 * </ul>
 */
public class FixtureEsaClient implements EsaOutreachClient, ImageDownloader {

    public static final Instant CAPTURED_AT = Instant.parse("2026-10-07T22:28:30Z");

    private final AtomicInteger searchCalls = new AtomicInteger();
    private final AtomicInteger avmCalls = new AtomicInteger();
    private final AtomicInteger downloads = new AtomicInteger();
    private final Set<String> missingAvm = new HashSet<>();

    private static String prefix(EsaProvider p) {
        return p.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public RawPayload search(EsaSite site, String subjectTerm) {
        searchCalls.incrementAndGet();
        String file = "esa/" + prefix(site.provider()) + "_search_"
                + subjectTerm.toLowerCase(Locale.ROOT).replace(' ', '_') + ".html";
        if (!Fixtures.exists(file)) {
            throw new SourceQueryException("ESA (fixture): sem busca capturada em " + file);
        }
        return RawPayload.of(DATASET_SEARCH, site.searchUrl(subjectTerm).toString(), subjectTerm, Fixtures.read(file),
                RawPayload.BodyFormat.TEXT, CAPTURED_AT);
    }

    @Override
    public RawPayload avm(EsaSite site, String imageId) {
        avmCalls.incrementAndGet();
        String file = "esa/" + prefix(site.provider()) + "_avm_" + imageId + ".json";
        if (!Fixtures.exists(file)) {
            missingAvm.add(imageId);
            throw new SourceQueryException("ESA (fixture): sem AVM capturada para " + imageId);
        }
        String body = Fixtures.read(file);
        return RawPayload.of(DATASET_AVM, site.avmUrl(imageId).toString(), imageId, body,
                AvmJsonParser.isJson(body) ? RawPayload.BodyFormat.JSON : RawPayload.BodyFormat.TEXT, CAPTURED_AT);
    }

    @Override
    public DownloadedImage download(URI url, String service) {
        downloads.incrementAndGet();
        for (EsaProvider p : EsaProvider.values()) {
            String path = url.getPath();
            String marker = "/archives/images/screen/";
            if (path.startsWith(marker) && url.getHost().contains(p == EsaProvider.HUBBLE ? "esahubble" : "esawebb")) {
                String id = path.substring(marker.length()).replace(".jpg", "");
                String file = "esa/" + prefix(p) + "_screen_" + id + ".jpg";
                if (Fixtures.exists(file)) {
                    byte[] bytes = Fixtures.readBytes(file);
                    return new DownloadedImage(bytes, "image/jpeg", url.toString(), ImageFileStore.sha256Hex(bytes),
                            Instant.parse("2026-10-08T00:26:28Z"));
                }
            }
        }
        throw new SourceQueryException(service + " (fixture): sem JPG capturado para " + url);
    }

    public int searchCalls() {
        return searchCalls.get();
    }

    public int avmCalls() {
        return avmCalls.get();
    }

    public int downloads() {
        return downloads.get();
    }

    public Set<String> missingAvm() {
        return Set.copyOf(missingAvm);
    }

    public void reset() {
        searchCalls.set(0);
        avmCalls.set(0);
        downloads.set(0);
        missingAvm.clear();
    }
}
