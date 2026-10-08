package dev.galaxyexplorer.ingestion.connector.hips2fits;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.galaxyexplorer.ingestion.connector.image.ImageTarget;

/**
 * Decide campo e survey do recorte de um objeto. Puro.
 * <ul>
 *   <li>Campo: com tamanho angular, {@code fator x eixo maior}, limitado a [min, max]; sem, o padrao.</li>
 *   <li>Survey: o primeiro da lista de preferencia que cobre a declinacao do recorte inteiro
 *       ({@code dec - fov/2 >= minDec}) e aceita o campo ({@code fov <= maxFov}).</li>
 * </ul>
 */
public final class CutoutPlanner {

    private final List<HipsSurvey> preference;
    private final double factor;
    private final double minFovDeg;
    private final double maxFovDeg;
    private final double defaultFovDeg;
    private final int width;
    private final int height;

    public CutoutPlanner(List<HipsSurvey> preference, double factor, double minFovDeg, double maxFovDeg,
                         double defaultFovDeg, int width, int height) {
        this.preference = List.copyOf(preference);
        this.factor = factor;
        this.minFovDeg = minFovDeg;
        this.maxFovDeg = maxFovDeg;
        this.defaultFovDeg = defaultFovDeg;
        this.width = width;
        this.height = height;
    }

    /** Vazio se o objeto nao tem posicao ou nenhum survey cobre o recorte. */
    public Optional<CutoutPlan> plan(ImageTarget t) {
        if (!t.hasPosition()) {
            return Optional.empty();
        }
        double fov;
        String basis;
        Long measurementId = null;
        if (t.angSizeMajorArcmin() != null && t.angSizeMajorArcmin() > 0) {
            double raw = t.angSizeMajorArcmin() / 60.0 * factor;
            fov = Math.max(minFovDeg, Math.min(maxFovDeg, raw));
            basis = num(factor) + " × tamanho angular (" + (t.angSizeSource() == null ? "" : t.angSizeSource() + " ")
                    + "ang_size_major = " + num(t.angSizeMajorArcmin()) + "′)";
            if (fov != raw) {
                basis += "; limitado ao intervalo " + num(minFovDeg) + "°–" + num(maxFovDeg) + "°";
            }
            measurementId = t.angSizeMeasurementId();
        } else {
            fov = defaultFovDeg;
            basis = "padrão " + num(defaultFovDeg) + "°: objeto sem tamanho angular no catálogo";
        }
        double fovFinal = fov;
        Optional<HipsSurvey> survey = preference.stream()
                .filter(s -> t.decDeg() - fovFinal / 2 >= s.minDecDeg() && fovFinal <= s.maxFovDeg())
                .findFirst();
        String basisFinal = basis;
        Long idFinal = measurementId;
        return survey.map(s -> new CutoutPlan(s, t.raDeg(), t.decDeg(), fovFinal, basisFinal, idFinal, width, height));
    }

    /** Numero para texto em portugues ({@code 1,25}), no maximo 4 casas. */
    static String num(double v) {
        DecimalFormat f = new DecimalFormat("0.####", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));
        return f.format(v);
    }
}
