package dev.galaxyexplorer.ingestion.connector.image;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import dev.galaxyexplorer.ingestion.connector.HttpResponses;
import dev.galaxyexplorer.ingestion.connector.ResilientCall;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;

/**
 * GET com as regras de rede da ingestao de imagens, num lugar so:
 * <ul>
 *   <li>host na {@link HostAllowlist} ANTES de cada requisicao;</li>
 *   <li>o {@link RestClient} recebido NAO segue redirecionamento (HttpClient com
 *       {@code Redirect.NEVER}); 3xx e seguido aqui, ate {@value #MAX_REDIRECTS} saltos, e cada
 *       {@code Location} passa de novo pela allowlist (fecha SSRF por redirecionamento);</li>
 *   <li>le no maximo {@code maxBytes + 1} bytes (excedeu = rejeita, sem carregar o resto);</li>
 *   <li>cada salto passa por retry/circuit breaker/limitador da fonte ({@link ResilientCall});</li>
 *   <li>status: regra unica de {@link HttpResponses#throwIfFailed}.</li>
 * </ul>
 */
public class GuardedHttpClient {

    public static final int MAX_REDIRECTS = 3;
    private static final int MAX_ERROR_BYTES = 4096;

    /** Resposta 2xx. @param finalUrl URL que respondeu (depois dos redirecionamentos) */
    public record Response(byte[] body, MediaType contentType, URI finalUrl, Instant retrievedAt) {

        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private record Hop(Response response, URI redirect) {
    }

    private final RestClient restClient;
    private final HostAllowlist allowlist;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Clock clock;

    public GuardedHttpClient(RestClient restClient, HostAllowlist allowlist, RateLimiter rateLimiter,
                             CircuitBreaker circuitBreaker, Retry retry, Clock clock) {
        this.restClient = restClient;
        this.allowlist = allowlist;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.clock = clock;
    }

    public RateLimiter rateLimiter() {
        return rateLimiter;
    }

    public Response get(URI url, String service, long maxBytes) {
        URI current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!allowlist.allows(current)) {
                throw new SourceQueryException(service + ": endereço fora da lista de hosts permitidos: "
                        + describe(current));
            }
            URI target = current;
            Hop result = ResilientCall.execute(retry, circuitBreaker, rateLimiter,
                    () -> fetchOnce(target, service, maxBytes));
            if (result.response() != null) {
                return result.response();
            }
            current = result.redirect();
        }
        throw new SourceQueryException(service + ": mais de " + MAX_REDIRECTS + " redirecionamentos a partir de "
                + describe(url));
    }

    private Hop fetchOnce(URI uri, String service, long maxBytes) {
        try {
            return restClient.get().uri(uri).exchange((request, response) -> {
                if (response.getStatusCode().is3xxRedirection()) {
                    String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                    if (location == null || location.isBlank()) {
                        throw new SourceQueryException(service + ": HTTP " + response.getStatusCode().value()
                                + " sem Location");
                    }
                    return new Hop(null, uri.resolve(location.trim()));
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                    String body = new String(readAtMost(response.getBody(), MAX_ERROR_BYTES), StandardCharsets.UTF_8);
                    HttpResponses.throwIfFailed(service, response.getStatusCode(), body);
                }
                long declared = response.getHeaders().getContentLength();
                if (declared > maxBytes) {
                    throw new SourceQueryException(service + ": resposta de " + declared + " bytes passa do limite de "
                            + maxBytes);
                }
                byte[] body = readAtMost(response.getBody(), maxBytes + 1);
                if (body.length > maxBytes) {
                    throw new SourceQueryException(service + ": resposta passa do limite de " + maxBytes + " bytes");
                }
                return new Hop(new Response(body, response.getHeaders().getContentType(), uri, clock.instant()), null);
            });
        } catch (ResourceAccessException e) {
            throw new SourceUnavailableException(service + " inacessível: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new SourceQueryException(service + ": Location inválido: " + e.getMessage(), e);
        }
    }

    static byte[] readAtMost(InputStream in, long limit) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        int cap = (int) Math.min(limit, Integer.MAX_VALUE - 8);
        byte[] buffer = new byte[Math.min(cap, 64 * 1024)];
        int length = 0;
        while (length < cap) {
            if (length == buffer.length) {
                buffer = Arrays.copyOf(buffer, (int) Math.min((long) buffer.length * 2, cap));
            }
            int read = in.read(buffer, length, buffer.length - length);
            if (read < 0) {
                break;
            }
            length += read;
        }
        return Arrays.copyOf(buffer, length);
    }

    /** So esquema + host + caminho: a mensagem vai para log/error_summary, sem query string arbitraria. */
    static String describe(URI uri) {
        if (uri == null) {
            return "null";
        }
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                + (uri.getRawPath() == null ? "" : uri.getRawPath());
    }
}
