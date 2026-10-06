package dev.galaxyexplorer.ingestion.connector.simbad;

import static dev.galaxyexplorer.ingestion.connector.simbad.SimbadTestSupport.BETELGEUSE;
import static dev.galaxyexplorer.ingestion.connector.simbad.SimbadTestSupport.M31;
import static dev.galaxyexplorer.ingestion.connector.simbad.SimbadTestSupport.M82;
import static dev.galaxyexplorer.ingestion.connector.simbad.SimbadTestSupport.M87;
import static dev.galaxyexplorer.ingestion.connector.simbad.SimbadTestSupport.SGR_A_STAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.catalog.Epistemic;
import dev.galaxyexplorer.ingestion.connector.FetchedBatch;
import dev.galaxyexplorer.ingestion.connector.NormalizationOutcome;
import dev.galaxyexplorer.ingestion.connector.NormalizedMeasurement;
import dev.galaxyexplorer.ingestion.connector.NormalizedObject;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.Resolution;
import dev.galaxyexplorer.support.FixtureSesameClient;
import dev.galaxyexplorer.support.FixtureTapClient;

/** Sobre as respostas REAIS gravadas do SIMBAD (amostra de 11 objetos). */
class SimbadMapperTest {

    private static FetchedBatch batch;
    private static Map<Long, NormalizedObject> accepted;

    @BeforeAll
    static void normalizeSample() {
        SimbadConnector connector = SimbadTestSupport.connector(new FixtureTapClient(), new FixtureSesameClient());
        Resolution resolution = connector.resolve(SimbadTestSupport.SAMPLE_QUERIES);
        batch = connector.fetch(resolution.resolved());
        accepted = new HashMap<>();
        for (NormalizationOutcome o : SimbadMapper.normalize(batch)) {
            assertThat(o).isInstanceOf(NormalizationOutcome.Accepted.class);
            NormalizedObject obj = ((NormalizationOutcome.Accepted) o).object();
            accepted.put(obj.ref().sourceInternalId(), obj);
        }
    }

    private static List<NormalizedMeasurement> measurements(long oid, String property) {
        return accepted.get(oid).measurements().stream().filter(m -> m.propertyCode().equals(property)).toList();
    }

    private static NormalizedMeasurement single(long oid, String property) {
        List<NormalizedMeasurement> found = measurements(oid, property);
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    @Test
    void allElevenSampleObjectsAccepted() {
        assertThat(accepted).hasSize(11);
    }

    @Test
    void m31TypeAndMorphologyComeRawFromSource() {
        NormalizedObject m31 = accepted.get(M31);
        assertThat(m31.sourceType()).isEqualTo("AGN");
        assertThat(m31.morphType()).isEqualTo("SA(s)b");
        assertThat(m31.mainId()).isEqualTo("M  31"); // basic.main_id (validado), nao o <oname> do Sesame
        assertThat(m31.sourceObjectUrl()).isEqualTo("https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M%20%2031");
        assertThat(m31.identifiers()).anySatisfy(i -> {
            assertThat(i.identifier()).isEqualTo("NAME Andromeda");
            assertThat(i.isCommonName()).isTrue();
        });
        assertThat(m31.identifiers()).anySatisfy(i -> {
            assertThat(i.identifier()).isEqualTo("M  31");
            assertThat(i.isCommonName()).isFalse();
        });
    }

    @Test
    void sgrAStarIsXraySourceWithPositionOnly() {
        NormalizedObject sgr = accepted.get(SGR_A_STAR);
        assertThat(sgr.sourceType()).isEqualTo("X");
        assertThat(sgr.position()).isPresent();
        assertThat(sgr.position().get().frame()).isEqualTo("ICRS");
        assertThat(sgr.position().get().bibcode()).isEqualTo("2011AJ....142...35P");
    }

    @Test
    void betelgeuseParallaxWithReference() {
        NormalizedObject betelgeuse = accepted.get(BETELGEUSE);
        assertThat(betelgeuse.sourceType()).isEqualTo("s*r");
        NormalizedMeasurement plx = single(BETELGEUSE, "parallax");
        assertThat(plx.valueNum()).isEqualTo(6.55);
        assertThat(plx.errMinus()).isEqualTo(0.83);
        assertThat(plx.errPlus()).isEqualTo(0.83);
        assertThat(plx.bibcode()).isEqualTo("2007A&A...474..653V");
        assertThat(plx.epistemic()).isEqualTo(Epistemic.OBSERVED);
        assertThat(plx.originalUnit()).isEqualTo("mas");
        assertThat(single(BETELGEUSE, "spectral_type").valueText()).isEqualTo("M1-M2Ia-Iab");
    }

    @Test
    void magnitudesUseBandAsCaseSensitiveQualifier() {
        NormalizedMeasurement v = measurements(M31, "app_mag").stream().filter(m -> m.qualifier().equals("V"))
                .findFirst().orElseThrow();
        assertThat(v.valueNum()).isEqualTo(3.44);
        assertThat(v.errMinus()).isEqualTo(0.03);
        assertThat(v.bibcode()).isEqualTo("2007ApJS..173..185G");
        assertThat(v.dataset()).isEqualTo("flux");
        // M87 tem 'g' (SDSS) e 'G' (Gaia) — bandas diferentes
        assertThat(measurements(M87, "app_mag")).extracting(NormalizedMeasurement::qualifier).contains("g", "G");
    }

    @Test
    void mesDistanceConvertedToParsecsPreservingOriginal() {
        List<NormalizedMeasurement> distances = measurements(M31, "distance");
        NormalizedMeasurement mpc = distances.stream()
                .filter(m -> m.dataset().equals("mesDistance:2007ApJS..173..185G:")).findFirst().orElseThrow();
        assertThat(mpc.valueNum()).isCloseTo(780000.0, within(1e-6));
        assertThat(mpc.originalValue()).isEqualTo("0.78");
        assertThat(mpc.originalUnit()).isEqualTo("Mpc");
        assertThat(mpc.epistemic()).isEqualTo(Epistemic.DERIVED_BY_SOURCE);
        assertThat(mpc.payloadDataset()).isEqualTo("mesDistance");

        // minus_err vem negativo no SIMBAD (-30 / +30 kpc): guardamos a magnitude, em pc
        NormalizedMeasurement kpc = distances.stream()
                .filter(m -> m.dataset().equals("mesDistance:2015MNRAS.451..724W:Cep")).findFirst().orElseThrow();
        assertThat(kpc.valueNum()).isCloseTo(731000.0, within(1e-6)); // a primeira das duas linhas da mesma ref/metodo
        assertThat(kpc.errMinus()).isCloseTo(30000.0, within(1e-6));
        assertThat(kpc.errPlus()).isCloseTo(30000.0, within(1e-6));
        assertThat(kpc.method()).isEqualTo("Cep");
        assertThat(accepted.get(M31).issues()).anyMatch(i -> i.contains("2015MNRAS.451..724W")
                && i.contains("repetida"));
    }

    @Test
    void rvzTypeDecidesObservedVersusDerived() {
        // M31: rvz_type 'v' -> velocidade medida (com erro), redshift derivado (sem erro)
        NormalizedMeasurement m31v = single(M31, "radial_velocity");
        assertThat(m31v.epistemic()).isEqualTo(Epistemic.OBSERVED);
        assertThat(m31v.valueNum()).isEqualTo(-300.0);
        assertThat(m31v.errMinus()).isEqualTo(4.0);
        NormalizedMeasurement m31z = single(M31, "redshift");
        assertThat(m31z.epistemic()).isEqualTo(Epistemic.DERIVED_BY_SOURCE);
        assertThat(m31z.errMinus()).isNull();
        // M82: rvz_type 'z' -> redshift medido; rvz_err (9.0E-5) e erro de redshift, nao km/s
        NormalizedMeasurement m82z = single(M82, "redshift");
        assertThat(m82z.epistemic()).isEqualTo(Epistemic.OBSERVED);
        assertThat(m82z.errMinus()).isEqualTo(9.0E-5);
        NormalizedMeasurement m82v = single(M82, "radial_velocity");
        assertThat(m82v.epistemic()).isEqualTo(Epistemic.DERIVED_BY_SOURCE);
        assertThat(m82v.method()).isEqualTo("relativistic conversion from z by SIMBAD (rvz_type=z)");
        assertThat(m82v.errMinus()).isNull();
    }

    @Test
    void rvzTypeCIsCzSoRedshiftIsMeasuredAndVelocityDerived() {
        // Fixture real de M51, M81, M94 (os 3 Messier com rvz_type 'c').
        SimbadConnector connector = SimbadTestSupport.connector(new FixtureTapClient(), new FixtureSesameClient());
        FetchedBatch cz = connector.fetch(connector.resolve(List.of("M51", "M81", "M94")).resolved());
        Map<Long, NormalizedObject> objects = new HashMap<>();
        for (NormalizationOutcome o : SimbadMapper.normalize(cz)) {
            NormalizedObject obj = ((NormalizationOutcome.Accepted) o).object();
            objects.put(obj.ref().sourceInternalId(), obj);
        }
        assertThat(objects).hasSize(3);
        NormalizedObject m51 = objects.get(587337L);
        NormalizedMeasurement z = m51.measurements().stream().filter(m -> m.propertyCode().equals("redshift"))
                .findFirst().orElseThrow();
        assertThat(z.epistemic()).isEqualTo(Epistemic.OBSERVED);
        assertThat(z.valueNum()).isEqualTo(0.0017445402178863352);
        assertThat(z.errMinus()).isEqualTo(6.671282E-6); // rvz_err e erro em z
        assertThat(z.method()).isEqualTo("SIMBAD rvz_type=c");
        NormalizedMeasurement v = m51.measurements().stream()
                .filter(m -> m.propertyCode().equals("radial_velocity")).findFirst().orElseThrow();
        assertThat(v.epistemic()).isEqualTo(Epistemic.DERIVED_BY_SOURCE);
        assertThat(v.valueNum()).isEqualTo(522.5438034260235);
        assertThat(v.errMinus()).isNull();
        assertThat(v.method()).isEqualTo("relativistic conversion from z by SIMBAD (rvz_type=c)");
        // a velocidade gravada e a conversao relativistica de z, nao c*z (523.0)
        double c = 299792.458;
        double onePlusZ2 = Math.pow(1 + z.valueNum(), 2);
        assertThat(v.valueNum()).isCloseTo(c * (onePlusZ2 - 1) / (onePlusZ2 + 1), within(1e-3));
        assertThat(objects.values()).allSatisfy(o -> {
            assertThat(o.issues()).noneMatch(i -> i.contains("rvz_type"));
            assertThat(o.measurements()).anyMatch(m -> m.propertyCode().equals("redshift")
                    && m.epistemic() == Epistemic.OBSERVED);
        });
    }

    @Test
    void everyValueHasDatasetAndPayloadDatasetAndOriginal() {
        for (NormalizedObject o : accepted.values()) {
            for (NormalizedMeasurement m : o.measurements()) {
                assertThat(m.dataset()).isNotBlank();
                assertThat(m.payloadDataset()).isIn("basic", "flux", "mesDistance");
                assertThat(m.originalValue()).isNotBlank();
            }
        }
    }

    /** Normaliza a amostra com o corpo real de basic alterado em memoria. */
    private static Map<Long, NormalizedObject> normalizeWithBasic(String tamperedBasic) {
        RawPayload basic = batch.payloadsByDataset().get("basic");
        assertThat(tamperedBasic).isNotEqualTo(basic.body());
        Map<String, RawPayload> payloads = new HashMap<>(batch.payloadsByDataset());
        payloads.put("basic", RawPayload.of("basic", basic.requestUrl(), basic.queryText(), tamperedBasic,
                RawPayload.BodyFormat.JSON, basic.retrievedAt()));
        Map<Long, NormalizedObject> out = new HashMap<>();
        for (NormalizationOutcome o : SimbadMapper.normalize(new FetchedBatch(batch.resolved(), payloads))) {
            NormalizedObject obj = ((NormalizationOutcome.Accepted) o).object();
            out.put(obj.ref().sourceInternalId(), obj);
        }
        return out;
    }

    @Test
    void negativePublishedUncertaintyIsDiscardedWithIssueButKeptAsOriginal() {
        // Betelgeuse real: plx 6.55 +- 0.83 -> erro trocado para -0.83 em memoria
        String body = batch.payloadsByDataset().get("basic").body()
                .replace("6.55,0.83,\"2007A&A...474..653V\"", "6.55,-0.83,\"2007A&A...474..653V\"");
        NormalizedObject betelgeuse = normalizeWithBasic(body).get(BETELGEUSE);

        NormalizedMeasurement plx = betelgeuse.measurements().stream()
                .filter(m -> m.propertyCode().equals("parallax")).findFirst().orElseThrow();
        assertThat(plx.valueNum()).isEqualTo(6.55);
        assertThat(plx.errMinus()).isNull();
        assertThat(plx.originalError()).isEqualTo("-0.83"); // a leitura dira "incerteza invalida", nao "ausente"
        assertThat(betelgeuse.issues()).anyMatch(i -> i.contains("plx_err negativo descartado: -0.83"));
    }

    @Test
    void nonNumericValueIsNeverDroppedSilently() {
        // M45 real: galdim_majaxis 76.86 -> texto em memoria
        String body = batch.payloadsByDataset().get("basic").body()
                .replace("76.86,76.86,\"2021MNRAS.504..356D\"", "\"n/a\",76.86,\"2021MNRAS.504..356D\"");
        NormalizedObject m45 = normalizeWithBasic(body).get(675533L);

        assertThat(m45.measurements()).noneMatch(m -> m.propertyCode().equals("ang_size_major"));
        assertThat(m45.measurements()).anyMatch(m -> m.propertyCode().equals("ang_size_minor"));
        assertThat(m45.issues()).anyMatch(i -> i.contains("galdim_majaxis descartado: valor não numérico 'n/a'"));
    }

    @Test
    void originalErrorIsKeptForPublishedUncertainties() {
        NormalizedMeasurement plx = single(BETELGEUSE, "parallax");
        assertThat(plx.originalError()).isEqualTo("0.83");
        NormalizedMeasurement kpc = measurements(M31, "distance").stream()
                .filter(m -> m.dataset().equals("mesDistance:2015MNRAS.451..724W:Cep")).findFirst().orElseThrow();
        assertThat(kpc.originalError()).isEqualTo("minus_err=-30; plus_err=30");
    }

    @Test
    void unitMismatchInAnyPayloadRejectsWholeBatch() {
        RawPayload basic = batch.payloadsByDataset().get("basic");
        // mesmo payload real, so a unidade de plx_value trocada em memoria
        String tampered = basic.body().replace(
                "{\"name\":\"plx_value\",\"description\":\"Parallax\",\"datatype\":\"DOUBLE\",\"unit\":\"mas\"",
                "{\"name\":\"plx_value\",\"description\":\"Parallax\",\"datatype\":\"DOUBLE\",\"unit\":\"arcsec\"");
        assertThat(tampered).isNotEqualTo(basic.body());
        Map<String, RawPayload> payloads = new HashMap<>(batch.payloadsByDataset());
        payloads.put("basic", RawPayload.of("basic", basic.requestUrl(), basic.queryText(), tampered,
                RawPayload.BodyFormat.JSON, basic.retrievedAt()));

        List<NormalizationOutcome> outcomes = SimbadMapper.normalize(new FetchedBatch(batch.resolved(), payloads));

        assertThat(outcomes).hasSize(11).allSatisfy(o -> {
            assertThat(o).isInstanceOf(NormalizationOutcome.Rejected.class);
            assertThat(((NormalizationOutcome.Rejected) o).reason())
                    .contains("UNIT_MISMATCH basic.plx_value: esperado mas, veio arcsec");
        });
    }
}
