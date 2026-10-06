package dev.galaxyexplorer.catalog.derived;

/** Resultado de tentar calcular a distancia a partir da paralaxe. */
public sealed interface DistanceComputation {

    /** Distancia calculada com intervalo assimetrico exato (pc). */
    record Computed(double valuePc, double errMinusPc, double errPlusPc, double snr) implements DistanceComputation {
    }

    /** @param snr S/N da paralaxe quando calculavel; {@code null} sem incerteza */
    record NotComputed(Reason reason, Double snr) implements DistanceComputation {
    }

    enum Reason {
        NO_UNCERTAINTY,
        NON_POSITIVE_PARALLAX,
        SNR_BELOW_THRESHOLD
    }
}
