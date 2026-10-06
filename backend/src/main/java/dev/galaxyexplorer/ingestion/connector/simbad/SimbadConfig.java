package dev.galaxyexplorer.ingestion.connector.simbad;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import dev.galaxyexplorer.ingestion.connector.tap.HttpTapClient;
import dev.galaxyexplorer.ingestion.connector.tap.TapClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Clientes HTTP do CDS e seus componentes de resiliencia, montados a mao
 * (Resilience4j core, sem starter). Os tres componentes sao singletons: seed,
 * importacao sob demanda, TAP e Sesame compartilham o MESMO limitador no
 * processo — o limite do CDS e por IP.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SimbadProperties.class)
public class SimbadConfig {

    @Bean
    RateLimiter simbadRateLimiter(SimbadProperties props) {
        return RateLimiter.of("cds", RateLimiterConfig.custom()
                .limitForPeriod(props.requestsPerSecond())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(30))
                .build());
    }

    @Bean
    Retry simbadRetry(SimbadProperties props) {
        return Retry.of("cds", RetryConfig.custom()
                .maxAttempts(props.retryMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(props.retryInitialBackoff(), 2.0))
                .retryExceptions(SourceUnavailableException.class)
                .build());
    }

    @Bean
    CircuitBreaker simbadCircuitBreaker() {
        return CircuitBreaker.of("cds", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .recordExceptions(SourceUnavailableException.class)
                .build());
    }

    @Bean
    RestClient cdsRestClient(SimbadProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, props.userAgent())
                .build();
    }

    @Bean
    TapClient simbadTapClient(RestClient cdsRestClient, SimbadProperties props, RateLimiter simbadRateLimiter,
                              CircuitBreaker simbadCircuitBreaker, Retry simbadRetry, Clock clock) {
        return new HttpTapClient(cdsRestClient, props.tapUrl(), props.maxRec(), simbadRateLimiter,
                simbadCircuitBreaker, simbadRetry, clock);
    }

    @Bean
    SesameClient simbadSesameClient(RestClient cdsRestClient, SimbadProperties props, RateLimiter simbadRateLimiter,
                                    CircuitBreaker simbadCircuitBreaker, Retry simbadRetry, Clock clock) {
        return new HttpSesameClient(cdsRestClient, props.sesameUrl(), simbadRateLimiter, simbadCircuitBreaker,
                simbadRetry, clock);
    }
}
