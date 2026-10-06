package dev.galaxyexplorer.ingestion.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

class ResilientCallTest {

    private static RateLimiter limiter(int perPeriod, Duration timeout) {
        return RateLimiter.of("test", RateLimiterConfig.custom().limitForPeriod(perPeriod)
                .limitRefreshPeriod(Duration.ofHours(1)).timeoutDuration(timeout).build());
    }

    private static Retry retry(int attempts) {
        return Retry.of("test", RetryConfig.custom().maxAttempts(attempts).waitDuration(Duration.ofMillis(1))
                .retryExceptions(SourceUnavailableException.class).build());
    }

    @Test
    void openCircuitBecomesSourceUnavailableAndIsNotRetried() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("cds");
        cb.transitionToOpenState();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> ResilientCall.execute(retry(3), cb, limiter(10, Duration.ZERO),
                () -> calls.incrementAndGet()))
                .isInstanceOf(SourceUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class)
                .hasMessageContaining("circuito aberto");
        assertThat(calls).hasValue(0);
    }

    @Test
    void exhaustedRateLimiterBecomesSourceUnavailable() {
        RateLimiter rl = limiter(1, Duration.ZERO);
        CircuitBreaker cb = CircuitBreaker.ofDefaults("cds");
        ResilientCall.execute(retry(1), cb, rl, () -> "primeira");

        assertThatThrownBy(() -> ResilientCall.execute(retry(1), cb, rl, () -> "segunda"))
                .isInstanceOf(SourceUnavailableException.class)
                .hasCauseInstanceOf(RequestNotPermitted.class);
    }

    @Test
    void retryIsOutermostSoEachAttemptPassesThroughCircuitBreakerAndRateLimiter() {
        RateLimiter rl = limiter(10, Duration.ZERO);
        CircuitBreaker cb = CircuitBreaker.ofDefaults("cds");
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> ResilientCall.execute(retry(3), cb, rl, () -> {
            calls.incrementAndGet();
            throw new SourceUnavailableException("HTTP 503");
        })).isInstanceOf(SourceUnavailableException.class);

        assertThat(calls).hasValue(3);
        // cada tentativa consumiu uma permissao do limitador (o retry nao fura o limite)...
        assertThat(rl.getMetrics().getAvailablePermissions()).isEqualTo(7);
        // ...e foi registrada pelo circuit breaker
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
    }

    @Test
    void permanentErrorsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> ResilientCall.execute(retry(3), CircuitBreaker.ofDefaults("cds"),
                limiter(10, Duration.ZERO), () -> {
                    calls.incrementAndGet();
                    throw new SourceQueryException("HTTP 400");
                })).isInstanceOf(SourceQueryException.class);
        assertThat(calls).hasValue(1);
    }
}
