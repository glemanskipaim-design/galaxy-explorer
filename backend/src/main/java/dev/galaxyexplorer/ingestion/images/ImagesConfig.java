package dev.galaxyexplorer.ingestion.images;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import dev.galaxyexplorer.ingestion.connector.esa.EsaOutreachClient;
import dev.galaxyexplorer.ingestion.connector.esa.EsaProvider;
import dev.galaxyexplorer.ingestion.connector.esa.EsaSite;
import dev.galaxyexplorer.ingestion.connector.esa.HttpEsaOutreachClient;
import dev.galaxyexplorer.ingestion.connector.hips2fits.CutoutPlanner;
import dev.galaxyexplorer.ingestion.connector.hips2fits.Hips2FitsClient;
import dev.galaxyexplorer.ingestion.connector.hips2fits.HipsSurvey;
import dev.galaxyexplorer.ingestion.connector.hips2fits.HttpHips2FitsClient;
import dev.galaxyexplorer.ingestion.connector.image.GuardedHttpClient;
import dev.galaxyexplorer.ingestion.connector.image.HostAllowlist;
import dev.galaxyexplorer.ingestion.connector.image.HttpImageDownloader;
import dev.galaxyexplorer.ingestion.connector.image.ImageDownloader;
import dev.galaxyexplorer.media.MediaProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Clientes HTTP das fontes de imagem, montados a mao (Resilience4j core, como o
 * SIMBAD). Um limitador/circuito/retry para o CDS hips2fits e outro para a ESA.
 * O {@link RestClient} daqui NAO segue redirecionamentos: quem segue (revalidando
 * a allowlist a cada salto) e o {@link GuardedHttpClient}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImageProperties.class)
public class ImagesConfig {

    @Bean
    RestClient imagesRestClient(ImageProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, props.userAgent())
                .build();
    }

    @Bean
    HostAllowlist imageHostAllowlist(ImageProperties props) {
        return new HostAllowlist(new HashSet<>(props.allowedHosts()));
    }

    @Bean
    Hips2FitsClient hips2FitsClient(RestClient imagesRestClient, HostAllowlist imageHostAllowlist,
                                    ImageProperties props, MediaProperties media, Clock clock) {
        GuardedHttpClient http = new GuardedHttpClient(imagesRestClient, imageHostAllowlist,
                rateLimiter("hips2fits", props.hips2fits().requestsPerSecond()), circuitBreaker("hips2fits"),
                retry("hips2fits", props), clock);
        return new HttpHips2FitsClient(http, new HttpImageDownloader(http, media.maxImageBytes()),
                props.hips2fits().baseUrls());
    }

    @Bean
    GuardedHttpClient esaHttpClient(RestClient imagesRestClient, HostAllowlist imageHostAllowlist,
                                    ImageProperties props, Clock clock) {
        return new GuardedHttpClient(imagesRestClient, imageHostAllowlist,
                rateLimiter("esa", props.esa().requestsPerSecond()), circuitBreaker("esa"), retry("esa", props), clock);
    }

    @Bean
    EsaOutreachClient esaOutreachClient(GuardedHttpClient esaHttpClient) {
        return new HttpEsaOutreachClient(esaHttpClient);
    }

    /** Download dos JPG "screen" da ESA — mesmo limitador da busca/AVM (mesma organizacao). */
    @Bean
    ImageDownloader esaImageDownloader(GuardedHttpClient esaHttpClient, MediaProperties media) {
        return new HttpImageDownloader(esaHttpClient, media.maxImageBytes());
    }

    @Bean
    CutoutPlanner cutoutPlanner(ImageProperties props) {
        ImageProperties.Hips2Fits h = props.hips2fits();
        List<HipsSurvey> surveys = h.surveys().stream()
                .map(s -> new HipsSurvey(s.code(), s.hipsId(), s.propertiesUrl(), s.shortLabel(), s.minDecDeg(),
                        s.maxFovDeg()))
                .toList();
        return new CutoutPlanner(surveys, h.fovFactor(), h.fovMinDeg(), h.fovMaxDeg(), h.fovDefaultDeg(), h.width(),
                h.height());
    }

    /** Os dois sites ESA da configuracao (nao e bean: {@code List<EsaSite>} confundiria a injecao por tipo). */
    static List<EsaSite> esaSites(ImageProperties props) {
        return List.of(new EsaSite(EsaProvider.HUBBLE, props.esa().hubble().baseUrl(), props.esa().hubble().cdnUrl()),
                new EsaSite(EsaProvider.WEBB, props.esa().webb().baseUrl(), props.esa().webb().cdnUrl()));
    }

    private static RateLimiter rateLimiter(String name, int perSecond) {
        return RateLimiter.of(name, RateLimiterConfig.custom()
                .limitForPeriod(perSecond)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(30))
                .build());
    }

    private static Retry retry(String name, ImageProperties props) {
        return Retry.of(name, RetryConfig.custom()
                .maxAttempts(props.retryMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(props.retryInitialBackoff(), 2.0))
                .retryExceptions(SourceUnavailableException.class)
                .build());
    }

    private static CircuitBreaker circuitBreaker(String name) {
        return CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .recordExceptions(SourceUnavailableException.class)
                .build());
    }
}
