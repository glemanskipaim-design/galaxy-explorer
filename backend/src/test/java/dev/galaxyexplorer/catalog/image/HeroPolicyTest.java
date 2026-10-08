package dev.galaxyexplorer.catalog.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.catalog.position.AngularSeparation;

class HeroPolicyTest {

    private static HeroCandidate cutout(long id) {
        return new HeroCandidate(id, ImageKind.SURVEY_CUTOUT, "CDS_HIPS2FITS", false, null, null, null);
    }

    private static HeroCandidate esa(long id, String source, Double coverage, LocalDate date) {
        return new HeroCandidate(id, ImageKind.PROCESSED_COMPOSITE, source, false, coverage, date, 1.0);
    }

    @Test
    void esaBeatsCutoutWhenItCoversEnough() {
        // M51 x heic0506a nos dados reais: 6,64' / 9,04' = 0,73
        assertThat(HeroPolicy.choose(List.of(cutout(1), esa(2, "ESA_HUBBLE", 0.73, LocalDate.of(2005, 4, 25)))))
                .contains(2L);
    }

    @Test
    void cutoutBeatsEsaThatCoversFarLessButEsaStaysInGallery() {
        // M31 x heic1502a: 0,093; M42 x heic0601a: 0,455 (abaixo do limiar de 0,5)
        List<HeroCandidate> all = List.of(esa(2, "ESA_HUBBLE", 0.093, LocalDate.of(2015, 1, 5)),
                esa(3, "ESA_HUBBLE", 0.455, LocalDate.of(2006, 1, 11)), cutout(1));
        assertThat(HeroPolicy.choose(all)).contains(1L);
        assertThat(HeroPolicy.order(all)).extracting(HeroCandidate::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    void unknownCoverageDoesNotDemoteEsa() {
        assertThat(HeroPolicy.choose(List.of(cutout(1), esa(2, "ESA_HUBBLE", null, null)))).contains(2L);
    }

    @Test
    void illustrationIsNeverHeroEvenCuratedAndGoesLast() {
        HeroCandidate art = new HeroCandidate(9, ImageKind.ARTIST_IMPRESSION, "ESA_HUBBLE", true, null,
                LocalDate.of(2020, 8, 13), null);
        assertThat(HeroPolicy.choose(List.of(art))).isEmpty();
        assertThat(HeroPolicy.order(List.of(art, cutout(1)))).extracting(HeroCandidate::id).containsExactly(1L, 9L);
    }

    @Test
    void curatedHeroWinsAndThenCoverageDateAndWebbOrder() {
        HeroCandidate curated = new HeroCandidate(5, ImageKind.SURVEY_CUTOUT, "CDS_HIPS2FITS", true, null, null, null);
        assertThat(HeroPolicy.choose(List.of(esa(2, "ESA_WEBB", 1.5, LocalDate.of(2022, 1, 1)), curated))).contains(5L);
        LocalDate same = LocalDate.of(2022, 10, 19);
        assertThat(HeroPolicy.choose(List.of(esa(2, "ESA_HUBBLE", 0.8, same), esa(3, "ESA_WEBB", 0.8, same))))
                .contains(3L);
        assertThat(HeroPolicy.choose(List.of(esa(2, "ESA_HUBBLE", 0.8, LocalDate.of(2023, 1, 1)),
                esa(3, "ESA_WEBB", 1.2, LocalDate.of(2005, 1, 1))))).contains(3L);
    }

    @Test
    void angularSeparationIdentities() {
        assertThat(AngularSeparation.degrees(10, 20, 10, 20)).isEqualTo(0.0);
        assertThat(AngularSeparation.degrees(0, 10, 0, 11)).isCloseTo(1.0, offset(1e-12));
        assertThat(AngularSeparation.degrees(0, 90, 180, 89)).isCloseTo(1.0, offset(1e-9)); // pelo polo
        assertThat(AngularSeparation.degrees(0, 0, 180, 0)).isCloseTo(180.0, offset(1e-9));
        assertThat(AngularSeparation.degrees(0, 60, 1, 60)).isCloseTo(0.5, offset(1e-4)); // ~cos(60°)
    }
}
