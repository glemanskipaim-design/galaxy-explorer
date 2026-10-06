package dev.galaxyexplorer.catalog.derived;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.catalog.derived.DistanceComputation.Computed;
import dev.galaxyexplorer.catalog.derived.DistanceComputation.NotComputed;
import dev.galaxyexplorer.catalog.derived.DistanceComputation.Reason;

/** Testa a formula/regra (numeros literais), nao afirma nada sobre o ceu. */
class DistanceFromParallaxTest {

    @Test
    void snrExactlyFiveComputes() {
        assertThat(DistanceFromParallax.compute(5.0, 1.0)).isInstanceOf(Computed.class);
    }

    @Test
    void snrJustBelowFiveDoesNot() {
        DistanceComputation r = DistanceFromParallax.compute(4.99, 1.0);
        assertThat(r).isInstanceOf(NotComputed.class);
        NotComputed nc = (NotComputed) r;
        assertThat(nc.reason()).isEqualTo(Reason.SNR_BELOW_THRESHOLD);
        assertThat(nc.snr()).isCloseTo(4.99, within(1e-12));
    }

    @Test
    void missingOrZeroUncertainty() {
        assertThat(((NotComputed) DistanceFromParallax.compute(6.0, null)).reason()).isEqualTo(Reason.NO_UNCERTAINTY);
        assertThat(((NotComputed) DistanceFromParallax.compute(6.0, 0.0)).reason()).isEqualTo(Reason.NO_UNCERTAINTY);
        assertThat(((NotComputed) DistanceFromParallax.compute(6.0, -1.0)).reason())
                .isEqualTo(Reason.NO_UNCERTAINTY);
    }

    @Test
    void nonPositiveParallax() {
        assertThat(((NotComputed) DistanceFromParallax.compute(0.0, 0.5)).reason())
                .isEqualTo(Reason.NON_POSITIVE_PARALLAX);
        assertThat(((NotComputed) DistanceFromParallax.compute(-0.2, 0.5)).reason())
                .isEqualTo(Reason.NON_POSITIVE_PARALLAX);
    }

    @Test
    void asymmetricIntervalIsExact() {
        Computed c = (Computed) DistanceFromParallax.compute(10.0, 1.0); // d = 100 pc
        assertThat(c.valuePc()).isCloseTo(100.0, within(1e-9));
        assertThat(c.errMinusPc()).isCloseTo(100.0 - 1000.0 / 11.0, within(1e-9)); // ~9.09
        assertThat(c.errPlusPc()).isCloseTo(1000.0 / 9.0 - 100.0, within(1e-9));   // ~11.11
        assertThat(c.errPlusPc()).isGreaterThan(c.errMinusPc());
        assertThat(c.snr()).isCloseTo(10.0, within(1e-12));
    }
}
