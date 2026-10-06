package dev.galaxyexplorer.catalog.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.catalog.measurement.MeasurementRow;
import dev.galaxyexplorer.catalog.measurement.PropertyDefinition;
import dev.galaxyexplorer.support.Fixtures;

/**
 * Regra de leitura (pura): motivo de ausencia da distancia derivado da mesma
 * funcao que decide o calculo. A paralaxe usada e a real de M54 na fixture
 * (0.053 +- 0.011 mas, S/N 4.82); aqui ela aparece SEM mesDistance de proposito,
 * para exercitar o caminho "nao calculada".
 */
class ObjectQueryServiceTest {

    private static final List<PropertyDefinition> DEFS = List.of(
            new PropertyDefinition("distance", "Distância", "pc", "numeric", "astrometry"),
            new PropertyDefinition("parallax", "Paralaxe", "mas", "numeric", "astrometry"),
            new PropertyDefinition("redshift", "Redshift (z)", null, "numeric", "kinematics"));

    private static MeasurementRow parallax(double value, Double err) {
        return parallax(value, err, err == null ? null : Double.toString(err));
    }

    /** @param originalError incerteza como a fonte publicou (pode ter sido descartada: err nulo) */
    private static MeasurementRow parallax(double value, Double err, String originalError) {
        return new MeasurementRow(1, UUID.randomUUID(), "parallax", "", value, err, err, null, null,
                Double.toString(value), "mas", originalError, "observed", null, List.of(), (short) 1, "SIMBAD",
                "basic", "https://x", null, "2021MNRAS.505.5978V", 10L, Fixtures.CAPTURED_AT, true);
    }

    private static PropertyView property(List<PropertyView> views, String code) {
        return views.stream().filter(v -> v.code().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void distanceAbsentBecauseParallaxSnrBelowFive() {
        List<PropertyView> views = ObjectQueryService.buildProperties(DEFS, List.of(parallax(0.053, 0.011)));

        PropertyView distance = property(views, "distance");
        assertThat(distance.available()).isFalse();
        assertThat(distance.absenceReason()).isEqualTo(AbsenceReason.PARALLAX_SNR_BELOW_5);
        assertThat(distance.absenceSnr()).isCloseTo(0.053 / 0.011, within(1e-9));
        assertThat(property(views, "parallax").available()).isTrue();
        assertThat(property(views, "parallax").preferred().bibcode()).isEqualTo("2021MNRAS.505.5978V");
        assertThat(property(views, "redshift").absenceReason()).isEqualTo(AbsenceReason.NOT_PROVIDED_BY_SOURCE);
    }

    @Test
    void distanceAbsentBecauseParallaxHasNoUncertainty() {
        List<PropertyView> views = ObjectQueryService.buildProperties(DEFS, List.of(parallax(0.053, null)));
        assertThat(property(views, "distance").absenceReason()).isEqualTo(AbsenceReason.PARALLAX_WITHOUT_UNCERTAINTY);
    }

    @Test
    void invalidPublishedUncertaintyIsNotReportedAsMissing() {
        // a fonte publicou plx_err = -0.011 (invalido): o mapper descartou (err nulo) mas guardou o original
        List<PropertyView> views = ObjectQueryService.buildProperties(DEFS, List.of(parallax(0.053, null, "-0.011")));
        assertThat(property(views, "distance").absenceReason()).isEqualTo(AbsenceReason.PARALLAX_UNCERTAINTY_INVALID);
        assertThat(AbsenceReason.PARALLAX_UNCERTAINTY_INVALID.messagePt()).contains("inválida");
    }

    @Test
    void noParallaxMeansNotProvided() {
        List<PropertyView> views = ObjectQueryService.buildProperties(DEFS, List.of());
        assertThat(views).hasSize(3).allSatisfy(v -> {
            assertThat(v.available()).isFalse();
            assertThat(v.absenceReason()).isEqualTo(AbsenceReason.NOT_PROVIDED_BY_SOURCE);
        });
    }
}
