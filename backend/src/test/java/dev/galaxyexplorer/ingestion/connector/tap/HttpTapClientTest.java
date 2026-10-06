package dev.galaxyexplorer.ingestion.connector.tap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

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

class HttpTapClientTest {

    private static final String URL = "https://simbad.cds.unistra.fr/simbad/sim-tap/sync";
    private static final Instant NOW = Instant.parse("2026-10-05T22:00:00Z");

    private MockRestServiceServer server;
    private HttpTapClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RateLimiter rl = RateLimiter.of("test", RateLimiterConfig.custom().limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofSeconds(1)).timeoutDuration(Duration.ofSeconds(1)).build());
        Retry retry = Retry.of("test", RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1))
                .retryExceptions(SourceUnavailableException.class).build());
        client = new HttpTapClient(builder.build(), URI.create(URL), 100000, rl, CircuitBreaker.ofDefaults("test"),
                retry, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void postsFormParamsAndKeepsExactBody() throws Exception {
        String adql = Fixtures.read("simbad/sample_basic.adql").trim();
        String body = Fixtures.read("simbad/sample_basic.json");
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of("REQUEST", "doQuery", "LANG", "ADQL", "FORMAT", "json",
                        "MAXREC", "100000", "QUERY", adql)))
                .andRespond(withSuccess(body, MediaType.parseMediaType("application/json;charset=UTF-8")));

        RawPayload payload = client.query("basic", adql);

        server.verify();
        assertThat(payload.body()).isEqualTo(body);
        assertThat(payload.queryText()).isEqualTo(adql);
        assertThat(payload.requestUrl()).isEqualTo(URL);
        assertThat(payload.dataset()).isEqualTo("basic");
        assertThat(payload.retrievedAt()).isEqualTo(NOW);
        assertThat(payload.format()).isEqualTo(RawPayload.BodyFormat.JSON);
        String expectedSha = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        assertThat(payload.sha256()).isEqualTo(expectedSha);
    }

    @Test
    void realAdqlErrorIsPermanentAndNotRetried() {
        // Resposta REAL do SIMBAD para ADQL invalida: HTTP 400, VOTable em application/xml.
        server.expect(once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.parseMediaType("application/xml;charset=UTF-8"))
                        .body(Fixtures.read("simbad/error_bad_adql.xml")));

        assertThatThrownBy(() -> client.query("basic", Fixtures.read("simbad/error_bad_adql.adql")))
                .isInstanceOf(SourceQueryException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("Incorrect ADQL query");
        server.verify();
    }

    @Test
    void serverErrorIsRetriedThenUnavailable() {
        server.expect(times(3), requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.query("basic", "SELECT 1")).isInstanceOf(SourceUnavailableException.class)
                .hasMessageContaining("HTTP 500");
        server.verify();
    }

    @Test
    void tooManyRequestsIsTransient() {
        server.expect(times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.query("basic", "SELECT 1")).isInstanceOf(SourceUnavailableException.class);
        server.verify();
    }

    @Test
    void successWithoutJsonContentTypeIsRejected() {
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(Fixtures.read("simbad/error_bad_adql.xml"),
                MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> client.query("basic", "SELECT 1")).isInstanceOf(SourceQueryException.class)
                .hasMessageContaining("Content-Type");
    }
}
