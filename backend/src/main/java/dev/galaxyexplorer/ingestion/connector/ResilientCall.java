package dev.galaxyexplorer.ingestion.connector;

import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;

/**
 * Aplica os decorators Resilience4j numa chamada HTTP, nesta ordem:
 * <pre>
 *   Retry (externo) -> CircuitBreaker -> RateLimiter (interno) -> chamada
 * </pre>
 * Cada tentativa do retry passa de novo pelo limitador, entao o retry nunca
 * fura o limite de requisicoes por segundo. Circuito aberto e limitador
 * esgotado viram {@link SourceUnavailableException}.
 */
public final class ResilientCall {

    private ResilientCall() {
    }

    public static <T> T execute(Retry retry, CircuitBreaker circuitBreaker, RateLimiter rateLimiter,
                                Supplier<T> call) {
        Supplier<T> limited = RateLimiter.decorateSupplier(rateLimiter, call);
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, limited);
        Supplier<T> retried = Retry.decorateSupplier(retry, guarded);
        try {
            return retried.get();
        } catch (CallNotPermittedException e) {
            throw new SourceUnavailableException("circuito aberto para " + circuitBreaker.getName()
                    + " — fonte falhando repetidamente, chamadas suspensas temporariamente", e);
        } catch (RequestNotPermitted e) {
            throw new SourceUnavailableException("limite de requisicoes (" + rateLimiter.getName()
                    + "): tempo de espera esgotado", e);
        }
    }
}
