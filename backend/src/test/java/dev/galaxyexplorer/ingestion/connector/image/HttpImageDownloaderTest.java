package dev.galaxyexplorer.ingestion.connector.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import dev.galaxyexplorer.media.ImageFileStore;
import dev.galaxyexplorer.support.Fixtures;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/** Regras de rede (allowlist, redirect manual, limites, tipo) servindo o JPG REAL do recorte de M51. */
class HttpImageDownloaderTest {

    private static final URI CUTOUT = URI.create(Fixtures.read("hips2fits/m51_panstarrs_160x120.url").trim());
    private static final byte[] JPG = Fixtures.readBytes("hips2fits/m51_panstarrs_160x120.jpg");

    private MockRestServiceServer server;
    private HttpImageDownloader downloader;

    private HttpImageDownloader build(long maxBytes) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GuardedHttpClient http = new GuardedHttpClient(builder.build(),
                new HostAllowlist(Set.of("alasky.cds.unistra.fr", "alaskybis.cds.unistra.fr")),
                RateLimiter.of("t", RateLimiterConfig.custom().limitForPeriod(100)
                        .limitRefreshPeriod(Duration.ofSeconds(1)).timeoutDuration(Duration.ofSeconds(1)).build()),
                CircuitBreaker.ofDefaults("t"),
                Retry.of("t", RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1))
                        .retryExceptions(SourceUnavailableException.class).build()),
                Clock.fixed(Instant.parse("2026-10-07T22:28:30Z"), ZoneOffset.UTC));
        return new HttpImageDownloader(http, maxBytes);
    }

    @BeforeEach
    void setUp() {
        downloader = build(8 * 1024 * 1024);
    }

    @Test
    void downloadsRealJpegWithSha() {
        server.expect(once(), requestTo(CUTOUT)).andRespond(withSuccess(JPG, MediaType.IMAGE_JPEG));
        DownloadedImage d = downloader.download(CUTOUT, "hips2fits");
        server.verify();
        assertThat(d.bytes()).isEqualTo(JPG);
        assertThat(d.sha256()).isEqualTo(ImageFileStore.sha256Hex(JPG));
        assertThat(d.requestUrl()).isEqualTo(CUTOUT.toString());
    }

    @Test
    void hostOutsideAllowlistIsRefusedWithoutAnyRequest() {
        assertThatThrownBy(() -> downloader.download(URI.create("https://evil.example/x.jpg"), "hips2fits"))
                .isInstanceOf(SourceQueryException.class).hasMessageContaining("fora da lista");
        assertThatThrownBy(() -> downloader.download(URI.create("http://alasky.cds.unistra.fr/x.jpg"), "hips2fits"))
                .isInstanceOf(SourceQueryException.class);
        assertThatThrownBy(() -> downloader.download(URI.create("https://alasky.cds.unistra.fr:8443/x"), "hips2fits"))
                .isInstanceOf(SourceQueryException.class);
        server.verify(); // nenhuma expectativa: nenhuma requisicao
    }

    @Test
    void redirectToForeignHostIsRefusedAndAllowedRedirectIsFollowed() {
        server.expect(once(), requestTo(CUTOUT)).andRespond(withStatus(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "https://169.254.169.254/latest/meta-data"));
        assertThatThrownBy(() -> downloader.download(CUTOUT, "hips2fits")).isInstanceOf(SourceQueryException.class)
                .hasMessageContaining("fora da lista");
        server.verify();

        downloader = build(8 * 1024 * 1024);
        URI mirror = URI.create(CUTOUT.toString().replace("alasky.", "alaskybis."));
        server.expect(once(), requestTo(CUTOUT)).andRespond(withStatus(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, mirror.toString()));
        server.expect(once(), requestTo(mirror)).andRespond(withSuccess(JPG, MediaType.IMAGE_JPEG));
        assertThat(downloader.download(CUTOUT, "hips2fits").requestUrl()).isEqualTo(mirror.toString());
        server.verify();
    }

    @Test
    void tooLargeWrongTypeAndFakeJpegAreRefused() {
        downloader = build(JPG.length - 1L);
        server.expect(once(), requestTo(CUTOUT)).andRespond(withSuccess(JPG, MediaType.IMAGE_JPEG));
        assertThatThrownBy(() -> downloader.download(CUTOUT, "hips2fits")).hasMessageContaining("limite");

        downloader = build(8 * 1024 * 1024);
        server.expect(once(), requestTo(CUTOUT)).andRespond(withSuccess(JPG, MediaType.TEXT_HTML));
        assertThatThrownBy(() -> downloader.download(CUTOUT, "hips2fits")).hasMessageContaining("esperado image/jpeg");

        downloader = build(8 * 1024 * 1024);
        byte[] html = Fixtures.read("esa/webb_search_m_16_noresults.html").getBytes();
        server.expect(once(), requestTo(CUTOUT)).andRespond(withSuccess(html, MediaType.IMAGE_JPEG));
        assertThatThrownBy(() -> downloader.download(CUTOUT, "hips2fits")).hasMessageContaining("não é JPEG");
    }

    @Test
    void realBadHipsAnswerIs400AndNotRetried() {
        server.expect(once(), requestTo(CUTOUT)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body(Fixtures.read("hips2fits/error_bad_hips.txt")));
        assertThatThrownBy(() -> downloader.download(CUTOUT, "hips2fits")).isInstanceOf(SourceQueryException.class)
                .hasMessageContaining("Unknown HiPS");
        server.verify();
    }

    @Test
    void serviceUnavailableIsRetriedThenUnavailable() {
        server.expect(times(3), requestTo(CUTOUT)).andRespond(withServiceUnavailable());
        assertThatThrownBy(() -> downloader.download(CUTOUT, "hips2fits"))
                .isInstanceOf(SourceUnavailableException.class);
        server.verify();
    }
}
