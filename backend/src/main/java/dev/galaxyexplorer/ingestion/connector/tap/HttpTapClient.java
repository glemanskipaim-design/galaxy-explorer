package dev.galaxyexplorer.ingestion.connector.tap;

import java.net.URI;
import java.time.Clock;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import dev.galaxyexplorer.ingestion.connector.HttpResponses;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.ResilientCall;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;

/**
 * Cliente TAP sincrono via HTTP POST. Cada fonte tem a sua instancia (seu
 * limitador, seu circuit breaker): o codigo e compartilhado, o estado nao.
 *
 * <p>Classificacao de status: {@link HttpResponses#throwIfFailed} (regra unica,
 * compartilhada com o Sesame). Alem dela, 2xx sem Content-Type JSON ->
 * {@link SourceQueryException} (fato observado no SIMBAD: erro de ADQL volta em
 * VOTable/XML). Erro de rede -> {@link SourceUnavailableException}.
 */
public class HttpTapClient implements TapClient {

    private final RestClient restClient;
    private final URI syncUrl;
    private final int maxRec;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Clock clock;

    public HttpTapClient(RestClient restClient, URI syncUrl, int maxRec, RateLimiter rateLimiter,
                         CircuitBreaker circuitBreaker, Retry retry, Clock clock) {
        this.restClient = restClient;
        this.syncUrl = syncUrl;
        this.maxRec = maxRec;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.clock = clock;
    }

    @Override
    public RawPayload query(String dataset, String adql) {
        return ResilientCall.execute(retry, circuitBreaker, rateLimiter, () -> post(dataset, adql));
    }

    /** Exposto para verificar que TAP e Sesame compartilham o mesmo limitador (o limite do CDS e por IP). */
    public RateLimiter rateLimiter() {
        return rateLimiter;
    }

    private RawPayload post(String dataset, String adql) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("REQUEST", "doQuery");
        form.add("LANG", "ADQL");
        form.add("FORMAT", "json");
        form.add("MAXREC", Integer.toString(maxRec));
        form.add("QUERY", adql);
        String service = "TAP " + syncUrl.getHost() + " ('" + dataset + "')";
        try {
            return restClient.post()
                    .uri(syncUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .exchange((request, response) -> {
                        String body = HttpResponses.readBody(response.getBody());
                        HttpResponses.throwIfFailed(service, response.getStatusCode(), body);
                        MediaType contentType = response.getHeaders().getContentType();
                        if (contentType == null || !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                            throw new SourceQueryException(service + " devolveu Content-Type " + contentType
                                    + " em vez de JSON: " + HttpResponses.abbreviate(body));
                        }
                        return RawPayload.of(dataset, syncUrl.toString(), adql, body, RawPayload.BodyFormat.JSON,
                                clock.instant());
                    });
        } catch (ResourceAccessException e) {
            throw new SourceUnavailableException(service + " inacessível: " + e.getMessage(), e);
        }
    }
}
