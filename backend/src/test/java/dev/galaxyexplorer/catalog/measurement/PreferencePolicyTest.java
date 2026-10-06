package dev.galaxyexplorer.catalog.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Regras/valores literais: testa a politica, nao afirma nada sobre o ceu. */
class PreferencePolicyTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-06-01T00:00:00Z");
    private static final short SIMBAD = 1;
    private static final short NED = 2;

    private static PreferenceCandidate c(long id, String prop, String q, short source, Double v, Double err,
                                         Instant t) {
        return new PreferenceCandidate(id, prop, q, source, v, err, err, t);
    }

    @Test
    void rankBeatsUncertainty() {
        var simbadPrecise = c(1, "redshift", "", SIMBAD, 0.001, 0.000001, T1);
        var nedLoose = c(2, "redshift", "", NED, 0.001, 0.0005, T1);
        var rules = List.of(new PriorityRule("redshift", null, NED, (short) 1),
                new PriorityRule("redshift", null, SIMBAD, (short) 2));
        assertThat(PreferencePolicy.choosePreferred(List.of(simbadPrecise, nedLoose), rules, null)).containsExactly(2L);
    }

    @Test
    void categoryRuleBeatsGenericRule() {
        var a = c(1, "redshift", "", SIMBAD, 0.001, null, T1);
        var b = c(2, "redshift", "", NED, 0.001, null, T1);
        var rules = List.of(new PriorityRule("redshift", null, NED, (short) 1),
                new PriorityRule("redshift", 7, SIMBAD, (short) 1));
        assertThat(PreferencePolicy.choosePreferred(List.of(a, b), rules, 7)).containsExactly(1L);
        assertThat(PreferencePolicy.choosePreferred(List.of(a, b), rules, 8)).containsExactly(2L);
    }

    @Test
    void sourceWithoutRuleComesAfterSourcesWithRule() {
        var ruled = c(1, "distance", "", NED, 100.0, 50.0, T1);
        var unruled = c(2, "distance", "", SIMBAD, 100.0, 1.0, T1);
        var rules = List.of(new PriorityRule("distance", null, NED, (short) 3));
        assertThat(PreferencePolicy.choosePreferred(List.of(unruled, ruled), rules, null)).containsExactly(1L);
    }

    @Test
    void smallerRelativeUncertaintyWinsAndMissingUncertaintyLoses() {
        var noErr = c(1, "distance", "", SIMBAD, 780.0, null, T2);
        var big = c(2, "distance", "", SIMBAD, 780.0, 100.0, T1);
        var small = c(3, "distance", "", SIMBAD, 760.0, 10.0, T1);
        assertThat(PreferencePolicy.choosePreferred(List.of(noErr, big, small), List.of(), null)).containsExactly(3L);
        assertThat(PreferencePolicy.choosePreferred(List.of(noErr, big), List.of(), null)).containsExactly(2L);
    }

    @Test
    void thenMostRecentThenLowestId() {
        var old = c(1, "spectral_type", "", SIMBAD, null, null, T1);
        var recent = c(2, "spectral_type", "", SIMBAD, null, null, T2);
        assertThat(PreferencePolicy.choosePreferred(List.of(old, recent), List.of(), null)).containsExactly(2L);
        var twin = c(3, "spectral_type", "", SIMBAD, null, null, T2);
        assertThat(PreferencePolicy.choosePreferred(List.of(twin, recent), List.of(), null)).containsExactly(2L);
    }

    @Test
    void exactlyOnePerPropertyAndQualifier() {
        var v1 = c(1, "app_mag", "V", SIMBAD, 3.4, 0.03, T1);
        var v2 = c(2, "app_mag", "V", SIMBAD, 3.5, 0.3, T1);
        var b = c(3, "app_mag", "B", SIMBAD, 4.3, 0.02, T1);
        var plx = c(4, "parallax", "", SIMBAD, 6.55, 0.83, T1);
        assertThat(PreferencePolicy.choosePreferred(List.of(v1, v2, b, plx), List.of(), null))
                .containsExactlyInAnyOrder(1L, 3L, 4L);
    }
}
