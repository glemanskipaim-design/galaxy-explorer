package dev.galaxyexplorer.ingestion.connector.hips2fits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.image.ImageTarget;
import dev.galaxyexplorer.support.Fixtures;
import dev.galaxyexplorer.support.RealTargets;

/** properties (arquivos reais), planejamento do recorte (objetos reais do SIMBAD) e URL. */
class Hips2FitsPureTest {

    static final HipsSurvey PANSTARRS = new HipsSurvey("PANSTARRS_COLOR", "CDS/P/PanSTARRS/DR1/color-z-zg-g",
            URI.create("https://alasky.cds.unistra.fr/Pan-STARRS/DR1/color-z-zg-g/properties"), "PanSTARRS via CDS",
            -30, 1.0);
    static final HipsSurvey DSS2 = new HipsSurvey("DSS2_COLOR", "CDS/P/DSS2/color",
            URI.create("https://alasky.cds.unistra.fr/DSS/DSSColor/properties"), "DSS2 via CDS", -90, 10);
    static final CutoutPlanner PLANNER = new CutoutPlanner(List.of(PANSTARRS, DSS2), 1.25, 0.1, 5.0, 0.25, 800, 600);

    @Test
    void dssPropertiesGiveLiteralCreditAndLicense() {
        HipsSurveyMetadata m = HipsPropertiesParser.toMetadata(
                HipsPropertiesParser.parse(Fixtures.read("hips2fits/dss2_color.properties")), DSS2);
        assertThat(m.obsTitle()).isEqualTo("DSS colored");
        assertThat(m.obsCopyright()).isEqualTo("Digitized Sky Survey - STScI/NASA, Colored & Healpixed by CDS");
        assertThat(m.obsCopyrightUrl()).isEqualTo("http://archive.stsci.edu/dss/copyright.html");
        assertThat(m.hipsLicense()).isEqualTo("ODbL-1.0");
        assertThat(m.hipsCopyright()).isEqualTo("CNRS/Unistra"); // licenciante do HiPS, != obs_copyright
        assertThat(m.creatorDid()).isEqualTo("ivo://CDS/P/DSS2/color");
    }

    @Test
    void missingHipsCopyrightIsNullNotPresumed() {
        // alteracao em memoria declarada: remove a linha hips_copyright do arquivo real
        String noCopyright = Fixtures.read("hips2fits/dss2_color.properties").replaceAll("(?m)^hips_copyright.*$", "");
        assertThat(HipsPropertiesParser.toMetadata(HipsPropertiesParser.parse(noCopyright), DSS2).hipsCopyright())
                .isNull();
    }

    @Test
    void commentedKeysAreIgnoredAndPanstarrsParses() {
        Map<String, String> props = HipsPropertiesParser.parse(Fixtures.read("hips2fits/panstarrs_color.properties"));
        assertThat(props).doesNotContainKey("#hips_service_url").doesNotContainKey("hips_service_url");
        HipsSurveyMetadata m = HipsPropertiesParser.toMetadata(props, PANSTARRS);
        assertThat(m.obsCopyright()).isEqualTo("PS1 Science Consortium");
        assertThat(m.hipsCopyright()).isEqualTo("CNRS/Unistra");
    }

    @Test
    void propertiesOfAnotherHipsOrWithoutLicenseAreRefused() {
        Map<String, String> dss = HipsPropertiesParser.parse(Fixtures.read("hips2fits/dss2_color.properties"));
        assertThatThrownBy(() -> HipsPropertiesParser.toMetadata(dss, PANSTARRS))
                .isInstanceOf(SourceQueryException.class).hasMessageContaining("outro HiPS");
        // alteracao em memoria declarada: remove a linha hips_license do arquivo real
        String noLicense = Fixtures.read("hips2fits/dss2_color.properties").replaceAll("(?m)^hips_license.*$", "");
        assertThatThrownBy(() -> HipsPropertiesParser.toMetadata(HipsPropertiesParser.parse(noLicense), DSS2))
                .isInstanceOf(SourceQueryException.class).hasMessageContaining("hips_license");
    }

    @Test
    void m51UsesItsAngularSizeAndPanstarrs() {
        CutoutPlan p = PLANNER.plan(RealTargets.of("cz", "M  51")).orElseThrow();
        assertThat(p.survey()).isEqualTo(PANSTARRS);
        assertThat(p.fovDeg()).isCloseTo(9.04379 / 60 * 1.25, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(p.fovBasis()).isEqualTo("1,25 × tamanho angular (SIMBAD ang_size_major = 9,0438′)");
    }

    @Test
    void m31IsClampedAndGoesToDss2() {
        CutoutPlan p = PLANNER.plan(RealTargets.of("sample", "M  31")).orElseThrow();
        assertThat(p.fovDeg()).isEqualTo(4.1569, org.assertj.core.data.Offset.offset(1e-3)); // 199,53/60*1,25
        assertThat(p.survey()).isEqualTo(DSS2); // campo > 1° (limite do PanSTARRS)
    }

    @Test
    void southernObjectGoesToDss2AndNoSizeUsesDefault() {
        CutoutPlan m54 = PLANNER.plan(RealTargets.of("sample", "M  54")).orElseThrow(); // dec -30,48
        assertThat(m54.survey()).isEqualTo(DSS2);
        CutoutPlan betelgeuse = PLANNER.plan(RealTargets.of("sample", "* alf Ori")).orElseThrow();
        assertThat(betelgeuse.fovDeg()).isEqualTo(0.25);
        assertThat(betelgeuse.fovBasis()).isEqualTo("padrão 0,25°: objeto sem tamanho angular no catálogo");
        assertThat(betelgeuse.derivedFromMeasurementId()).isNull();
    }

    @Test
    void noPositionNoPlan() {
        ImageTarget t = RealTargets.of("cz", "M  51");
        ImageTarget noPos = new ImageTarget(t.objectId(), t.slug(), t.displayName(), t.identifiers(), null, null,
                null, null, null, null);
        assertThat(PLANNER.plan(noPos)).isEqualTo(Optional.empty());
    }

    @Test
    void urlMatchesTheCapturedOne() {
        ImageTarget m51 = RealTargets.of("cz", "M  51");
        CutoutPlan captured = new CutoutPlan(PANSTARRS, m51.raDeg(), m51.decDeg(), 0.25, "x", null, 160, 120);
        assertThat(Hips2FitsUrls.cutout(URI.create("https://alasky.cds.unistra.fr/hips-image-services/hips2fits"),
                captured)).hasToString(Fixtures.read("hips2fits/m51_panstarrs_160x120.url").trim());
    }
}
