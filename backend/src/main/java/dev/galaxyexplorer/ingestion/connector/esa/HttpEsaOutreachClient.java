package dev.galaxyexplorer.ingestion.connector.esa;

import java.net.URI;

import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.image.GuardedHttpClient;

/**
 * ESA via HTTP, pelas regras de rede de {@link GuardedHttpClient} (allowlist,
 * sem redirecionamento automatico, limite de bytes, limitador "esa"). Fato
 * observado: a AVM vem com {@code Content-Type: text/plain} — por isso o tipo nao
 * e checado aqui; o parser decide.
 */
public class HttpEsaOutreachClient implements EsaOutreachClient {

    static final long MAX_TEXT_BYTES = 2L * 1024 * 1024;

    private final GuardedHttpClient http;

    public HttpEsaOutreachClient(GuardedHttpClient http) {
        this.http = http;
    }

    @Override
    public RawPayload search(EsaSite site, String subjectTerm) {
        URI url = site.searchUrl(subjectTerm);
        GuardedHttpClient.Response r = http.get(url, site.provider().label(), MAX_TEXT_BYTES);
        return RawPayload.of(DATASET_SEARCH, r.finalUrl().toString(), subjectTerm, r.text(), RawPayload.BodyFormat.TEXT,
                r.retrievedAt());
    }

    @Override
    public RawPayload avm(EsaSite site, String imageId) {
        URI url = site.avmUrl(imageId);
        GuardedHttpClient.Response r = http.get(url, site.provider().label(), MAX_TEXT_BYTES);
        String body = r.text();
        RawPayload.BodyFormat format = AvmJsonParser.isJson(body) ? RawPayload.BodyFormat.JSON
                : RawPayload.BodyFormat.TEXT;
        return RawPayload.of(DATASET_AVM, r.finalUrl().toString(), imageId, body, format, r.retrievedAt());
    }
}
