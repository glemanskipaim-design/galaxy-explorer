package dev.galaxyexplorer.ingestion.connector.simbad;

import java.net.URI;
import java.time.Clock;
import java.util.List;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import dev.galaxyexplorer.ingestion.connector.HttpResponses;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.ResilientCall;
import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;

/**
 * Sesame via HTTP GET. Fato observado: responde {@code Content-Type: text/plain}
 * (nao XML) e HTTP 200 mesmo quando nao reconhece o nome — por isso o
 * Content-Type nao e checado aqui; o parser decide. Status: regra unica de
 * {@link HttpResponses#throwIfFailed}.
 *
 * <p>Usa o MESMO {@link RateLimiter} do TAP do SIMBAD: o limite do CDS e por IP,
 * somando os dois servicos.
 */
public class HttpSesameClient implements SesameClient {

    private static final String SERVICE = "Sesame";

    private final RestClient restClient;
    private final URI baseUrl;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Clock clock;

    public HttpSesameClient(RestClient restClient, URI baseUrl, RateLimiter rateLimiter, CircuitBreaker circuitBreaker,
                            Retry retry, Clock clock) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.clock = clock;
    }

    @Override
    public RawPayload resolve(List<String> names) {
        URI uri = SesameUrls.build(baseUrl, names);
        return ResilientCall.execute(retry, circuitBreaker, rateLimiter, () -> get(uri, names));
    }

    /** Exposto para verificar que TAP e Sesame compartilham o mesmo limitador. */
    public RateLimiter rateLimiter() {
        return rateLimiter;
    }

    private RawPayload get(URI uri, List<String> names) {
        try {
            return restClient.get().uri(uri).exchange((request, response) -> {
                String body = HttpResponses.readBody(response.getBody());
                HttpResponses.throwIfFailed(SERVICE, response.getStatusCode(), body);
                return RawPayload.of(DATASET, uri.toString(), String.join("\n", names), body,
                        RawPayload.BodyFormat.TEXT, clock.instant());
            });
        } catch (ResourceAccessException e) {
            throw new SourceUnavailableException(SERVICE + " inacessível: " + e.getMessage(), e);
        }
    }
}
