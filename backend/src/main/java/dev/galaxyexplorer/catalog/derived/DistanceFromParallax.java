package dev.galaxyexplorer.catalog.derived;

import dev.galaxyexplorer.catalog.derived.DistanceComputation.Computed;
import dev.galaxyexplorer.catalog.derived.DistanceComputation.NotComputed;
import dev.galaxyexplorer.catalog.derived.DistanceComputation.Reason;

/**
 * Distancia = 1/paralaxe, so quando a paralaxe e confiavel (S/N >= 5).
 *
 * <p>Por que intervalo e nao "+- sigma": inverter a paralaxe distorce a
 * incerteza. A distancia e mais incerta "para longe" do que "para perto"
 * ({@code 1000/(plx-s) - d > d - 1000/(plx+s)}), entao o intervalo correto e
 * assimetrico.
 *
 * <p>Esta e a UNICA definicao do limiar de S/N no sistema: a escrita (calculo)
 * e a leitura (motivo de ausencia) chamam esta mesma funcao.
 */
public final class DistanceFromParallax {

    public static final double MIN_SNR = 5.0;

    private DistanceFromParallax() {
    }

    /**
     * @param parallaxMas    paralaxe em mas
     * @param parallaxErrMas incerteza (1 sigma) em mas; pode ser {@code null}
     */
    public static DistanceComputation compute(double parallaxMas, Double parallaxErrMas) {
        if (parallaxErrMas == null || parallaxErrMas <= 0.0) {
            return new NotComputed(Reason.NO_UNCERTAINTY, null);
        }
        if (parallaxMas <= 0.0) {
            return new NotComputed(Reason.NON_POSITIVE_PARALLAX, parallaxMas / parallaxErrMas);
        }
        double snr = parallaxMas / parallaxErrMas;
        if (snr < MIN_SNR) {
            return new NotComputed(Reason.SNR_BELOW_THRESHOLD, snr);
        }
        double d = 1000.0 / parallaxMas;
        double near = 1000.0 / (parallaxMas + parallaxErrMas);
        double far = 1000.0 / (parallaxMas - parallaxErrMas); // S/N >= 5 garante plx - s > 0
        return new Computed(d, d - near, far - d, snr);
    }
}
