package dev.galaxyexplorer.ingestion.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.support.Fixtures;
import dev.galaxyexplorer.support.Samples;

/** Pipeline completo: clientes de fixture (respostas reais) + PostgreSQL real. */
class IngestionPipelineIntegrationTest extends IntegrationTestBase {

    @Autowired
    IngestionPipeline pipeline;

    private IngestionReport ingestSample() {
        return pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.SEED, Samples.QUERIES));
    }

    private UUID objectBySourceRef(String ref) {
        return jdbc.sql("SELECT object_id FROM object_source_type WHERE source_ref = :ref").param("ref", ref)
                .query(UUID.class).single();
    }

    @Test
    void sampleIngestedAndUnknownNameMakesRunPartial() {
        IngestionReport report = ingestSample();

        assertThat(report.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(report.recordsOk()).isEqualTo(11);
        assertThat(report.recordsFailed()).isEqualTo(1);
        assertThat(report.outcomes()).filteredOn(o -> !o.ok()).singleElement().satisfies(o -> {
            assertThat(o.query()).isEqualTo(Samples.NOT_FOUND_QUERY);
            assertThat(o.notFound()).isTrue();
            assertThat(o.failureReason()).contains("não encontrado em SIMBAD");
        });
        Map<String, Object> run = jdbc.sql("SELECT * FROM ingestion_run WHERE id = :id").param("id", report.runId())
                .query().singleRow();
        assertThat(run.get("status")).isEqualTo("partial");
        assertThat(run.get("records_ok")).isEqualTo(11);
        assertThat(run.get("trigger")).isEqualTo("seed");
        assertThat(run.get("connector_version")).isEqualTo("simbad-tap/2");
        assertThat((String) run.get("error_summary")).contains(Samples.NOT_FOUND_QUERY);
        assertThat(count("celestial_object")).isEqualTo(11);
        assertThat(fixtureSesame().callCount()).isEqualTo(1);
        assertThat(fixtureTap().callCount()).isEqualTo(5); // tap_schema + basic, ident, flux, mesDistance
    }

    @Test
    void rawPayloadsStoredWithShaOfExactBody() {
        ingestSample();
        List<Map<String, Object>> raws = jdbc.sql("""
                SELECT dataset, content_sha256, request_url, query_text, body #>> '{}' AS text_body,
                       jsonb_typeof(body) AS kind
                FROM raw_payload ORDER BY id
                """).query().listOfRows();
        assertThat(raws).extracting(r -> r.get("dataset"))
                .containsExactly("tap_schema", "sesame", "basic", "ident", "flux", "mesDistance");
        Map<String, Object> basic = raws.get(2);
        assertThat(basic.get("content_sha256"))
                .isEqualTo(RawPayload.sha256Hex(Fixtures.read("simbad/sample_basic.json")));
        assertThat(basic.get("kind")).isEqualTo("object");
        assertThat((String) basic.get("query_text")).startsWith("SELECT oid, main_id");
        Map<String, Object> sesame = raws.get(1);
        assertThat(sesame.get("kind")).isEqualTo("string");
        assertThat(sesame.get("text_body")).isEqualTo(Fixtures.read("sesame/sample_resolve.xml"));
        assertThat(sesame.get("content_sha256"))
                .isEqualTo(RawPayload.sha256Hex(Fixtures.read("sesame/sample_resolve.xml")));
        assertThat((String) sesame.get("request_url")).startsWith("https://cds.unistra.fr/cgi-bin/nph-sesame/");
    }

    @Test
    void betelgeuseGetsComputedDistanceFromParallax() {
        ingestSample();
        UUID betelgeuse = objectBySourceRef("843879");
        Map<String, Object> plx = jdbc.sql("""
                SELECT id, value_num FROM measurement WHERE object_id = :id AND property_code = 'parallax'
                """).param("id", betelgeuse).query().singleRow();
        Map<String, Object> d = jdbc.sql("""
                SELECT value_num, err_minus, err_plus, epistemic, method, derived_from[1] AS from_id,
                       raw_payload_id, reference_id, is_preferred
                FROM measurement WHERE object_id = :id AND property_code = 'distance'
                """).param("id", betelgeuse).query().singleRow();
        assertThat(d.get("epistemic")).isEqualTo("computed_by_us");
        assertThat(d.get("method")).isEqualTo("1/plx");
        assertThat(d.get("from_id")).isEqualTo(plx.get("id"));
        assertThat((Double) d.get("value_num")).isCloseTo(1000.0 / 6.55, within(1e-9));
        assertThat((Double) d.get("err_minus")).isCloseTo(1000.0 / 6.55 - 1000.0 / (6.55 + 0.83), within(1e-9));
        assertThat((Double) d.get("err_plus")).isCloseTo(1000.0 / (6.55 - 0.83) - 1000.0 / 6.55, within(1e-9));
        assertThat(d.get("raw_payload_id")).isNotNull();
        assertThat(d.get("reference_id")).isNotNull(); // herdada da paralaxe (2007A&A...474..653V)
        assertThat(d.get("is_preferred")).isEqualTo(true);
    }

    @Test
    void lowSnrParallaxDoesNotProduceComputedDistance() {
        ingestSample();
        UUID m54 = objectBySourceRef("2395133"); // paralaxe real 0.053 +- 0.011 mas, S/N 4.82
        assertThat(jdbc.sql("SELECT count(*) FROM measurement WHERE object_id = :id AND property_code = 'parallax'")
                .param("id", m54).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM measurement WHERE object_id = :id AND epistemic = 'computed_by_us'
                """).param("id", m54).query(Integer.class).single()).isZero();
    }

    @Test
    void exactlyOnePreferredPerPropertyAndQualifier() {
        ingestSample();
        List<Map<String, Object>> groups = jdbc.sql("""
                SELECT object_id, property_code, qualifier, count(*) FILTER (WHERE is_preferred) AS preferred
                FROM measurement GROUP BY object_id, property_code, qualifier
                """).query().listOfRows();
        assertThat(groups).isNotEmpty().allSatisfy(g -> assertThat(g.get("preferred")).isEqualTo(1L));
    }

    @Test
    void classificationOfSample() {
        ingestSample();
        Map<String, String> categoryBySlug = new HashMap<>();
        jdbc.sql("SELECT o.slug, c.code FROM celestial_object o JOIN object_category c ON c.id = o.category_id")
                .query().listOfRows().forEach(r -> categoryBySlug.put((String) r.get("slug"), (String) r.get("code")));
        assertThat(categoryBySlug).containsEntry("messier-31", "galaxy_spiral")
                .containsEntry("messier-45", "cluster_open")
                .containsEntry("betelgeuse", "star_supergiant")
                .containsEntry("sgr-a", "x_ray_source")
                .containsEntry("messier-87", "galaxy_elliptical");
    }

    @Test
    void runningTwiceIsIdempotent() {
        ingestSample();
        List<String> tables = List.of("celestial_object", "measurement", "object_identifier", "object_position",
                "object_source_type", "bib_reference");
        Map<String, Integer> before = new HashMap<>();
        tables.forEach(t -> before.put(t, count(t)));
        List<UUID> idsBefore = jdbc.sql("SELECT id FROM celestial_object ORDER BY slug").query(UUID.class).list();

        IngestionReport second = ingestSample();

        assertThat(second.recordsOk()).isEqualTo(11);
        assertThat(second.outcomes()).filteredOn(ObjectOutcome::ok).noneMatch(ObjectOutcome::created);
        tables.forEach(t -> assertThat(count(t)).as(t).isEqualTo(before.get(t)));
        assertThat(jdbc.sql("SELECT id FROM celestial_object ORDER BY slug").query(UUID.class).list())
                .isEqualTo(idsBefore);
        assertThat(count("ingestion_run")).isEqualTo(2);
    }
}
