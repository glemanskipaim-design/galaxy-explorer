package dev.galaxyexplorer.ingestion.connector.simbad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import dev.galaxyexplorer.support.Fixtures;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/** HttpSesameClient servindo a resposta REAL gravada, com o Content-Type real (text/plain). */
class HttpSesameClientTest {

    private static final URI BASE = URI.create("https://cds.unistra.fr/cgi-bin/nph-sesame/-oxp/S");
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    private MockRestServiceServer server;
    private HttpSesameClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpSesameClient(builder.build(), BASE,
                RateLimiter.of("t", RateLimiterConfig.custom().limitForPeriod(100)
                        .limitRefreshPeriod(Duration.ofSeconds(1)).timeoutDuration(Duration.ofSeconds(1)).build()),
                CircuitBreaker.ofDefaults("t"),
                Retry.of("t", RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1))
                        .retryExceptions(SourceUnavailableException.class).build()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void getsEncodedUrlAndKeepsTextPlainBodyExactly() {
        String xml = Fixtures.read("sesame/vega.xml");
        server.expect(once(), requestTo(BASE + "?Vega")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(xml, MediaType.TEXT_PLAIN));

        RawPayload payload = client.resolve(List.of("Vega"));

        server.verify();
        assertThat(payload.body()).isEqualTo(xml);
        assertThat(payload.format()).isEqualTo(RawPayload.BodyFormat.TEXT);
        assertThat(payload.dataset()).isEqualTo("sesame");
        assertThat(payload.requestUrl()).isEqualTo(BASE + "?Vega");
        assertThat(payload.queryText()).isEqualTo("Vega");
        assertThat(payload.retrievedAt()).isEqualTo(NOW);
        assertThat(payload.sha256()).isEqualTo(RawPayload.sha256Hex(xml));
    }

    @Test
    void notFoundIsStill200AndLeftToTheParser() {
        String xml = Fixtures.read("sesame/not_found.xml");
        server.expect(once(), requestTo(BASE + "?XyzzyNotAnObject123")).andRespond(withSuccess(xml,
                MediaType.TEXT_PLAIN));

        assertThat(SesameXmlParser.parse(client.resolve(List.of("XyzzyNotAnObject123")).body()).get(0).found())
                .isFalse();
    }

    @Test
    void serverErrorIsRetriedThenUnavailable() {
        server.expect(times(3), requestTo(BASE + "?Vega")).andRespond(withServerError());

        assertThatThrownBy(() -> client.resolve(List.of("Vega"))).isInstanceOf(SourceUnavailableException.class)
                .hasMessageContaining("Sesame respondeu HTTP 500");
        server.verify();
    }

    @Test
    void clientErrorIsPermanentAndNotRetried() {
        server.expect(once(), requestTo(BASE + "?Vega")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.resolve(List.of("Vega"))).isInstanceOf(SourceQueryException.class)
                .hasMessageContaining("HTTP 403");
        server.verify();
    }

    @Test
    void tooManyRequestsIsTransient() {
        server.expect(times(3), requestTo(BASE + "?Vega")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.resolve(List.of("Vega"))).isInstanceOf(SourceUnavailableException.class);
        server.verify();
    }
}
