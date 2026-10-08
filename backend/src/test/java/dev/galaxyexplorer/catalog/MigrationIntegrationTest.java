package dev.galaxyexplorer.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import dev.galaxyexplorer.IntegrationTestBase;

class MigrationIntegrationTest extends IntegrationTestBase {

    @Test
    void allMigrationsApplied() {
        List<String> versions = jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
                .query(String.class).list();
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
    }

    @Test
    void schemaTablesExist() {
        List<String> tables = jdbc.sql("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
                """).query(String.class).list();
        assertThat(tables).contains("source", "ingestion_run", "raw_payload", "bib_reference", "object_category",
                "source_type_mapping", "celestial_object", "object_source_type", "object_identifier",
                "property_definition", "measurement", "property_source_priority", "object_position",
                "orbit_solution", "object_image", "import_job", "image_license", "hips_survey_snapshot");
    }

    @Test
    void sourceRefColumnFromAddendumExists() {
        String nullable = jdbc.sql("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'object_source_type' AND column_name = 'source_ref'
                """).query(String.class).single();
        assertThat(nullable).isEqualTo("NO");
    }

    @Test
    void trigramExtensionAndNativeUuid() {
        assertThat(jdbc.sql("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'").query(Integer.class)
                .single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT gen_random_uuid()").query(UUID.class).single()).isNotNull();
        assertThat(jdbc.sql("SELECT similarity('betelgeuse', 'betelgeuze')").query(Double.class).single())
                .isGreaterThan(0.3);
    }

    /** Insere uma linha minima em object_image (valores de teste; so as restricoes do banco estao em jogo). */
    private long insertImage(UUID objectId, String sourceImageId, String kind, boolean hero) {
        long runId = jdbc.sql("""
                INSERT INTO ingestion_run (source_id, trigger, status, connector_version)
                SELECT id, 'manual', 'ok', 'test' FROM source WHERE code = 'CDS_HIPS2FITS' RETURNING id
                """).query(Long.class).single();
        long payloadId = jdbc.sql("""
                INSERT INTO raw_payload (run_id, request_url, dataset, content_sha256, body, retrieved_at)
                VALUES (:run, 'https://example.invalid', 'test', repeat('a', 64), '{}'::jsonb, now()) RETURNING id
                """).param("run", runId).query(Long.class).single();
        return jdbc.sql("""
                INSERT INTO object_image (object_id, kind, image_url, page_url, provider, credit_verbatim, license_code,
                    is_hero, curated_by, retrieved_at, source_id, source_image_id, run_id, download_payload_id,
                    provider_short, rights_text, match_basis, storage_key, content_sha256, width_px, height_px,
                    byte_size, thumb_storage_key, thumb_sha256, thumb_width_px, thumb_height_px)
                SELECT :obj, :kind, 'u', 'p', 'prov', 'credit', 'CC-BY-4.0', :hero, 'manual', now(), s.id, :sid, :run,
                       :payload, 'p', 'r', 'm', 'k', repeat('b', 64), 1, 1, 1, 'k', repeat('c', 64), 1, 1
                FROM source s WHERE s.code = 'CDS_HIPS2FITS' RETURNING id
                """).param("obj", objectId).param("kind", kind).param("hero", hero).param("sid", sourceImageId)
                .param("run", runId).param("payload", payloadId).query(Long.class).single();
    }

    private UUID object(String slug) {
        return jdbc.sql("INSERT INTO celestial_object (slug, display_name) VALUES (:s, :s) RETURNING id")
                .param("s", slug).query(UUID.class).single();
    }

    @Test
    void v8ImageSourcesAndLicensesAreReferenceData() {
        assertThat(jdbc.sql("SELECT code FROM source WHERE code IN ('CDS_HIPS2FITS','ESA_HUBBLE','ESA_WEBB') ORDER BY code")
                .query(String.class).list()).containsExactly("CDS_HIPS2FITS", "ESA_HUBBLE", "ESA_WEBB");
        assertThat(jdbc.sql("SELECT code FROM image_license ORDER BY code").query(String.class).list())
                .containsExactly("CC-BY-4.0", "ODbL-1.0");
    }

    @Test
    void artistImpressionCanNeverBeHeroDatabaseCheck() {
        UUID id = object("teste-ilustracao");
        assertThatThrownBy(() -> insertImage(id, "x1", "artist_impression", true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("object_image_hero_kind");
        insertImage(id, "x2", "artist_impression", false); // fora do hero, aceita
    }

    @Test
    void onlyOneHeroPerObject() {
        UUID id = object("teste-dois-heros");
        insertImage(id, "a", "survey_cutout", true);
        assertThatThrownBy(() -> insertImage(id, "b", "processed_composite", true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("one_hero_image");
    }

    @Test
    void unknownLicenseIsRefused() {
        UUID id = object("teste-licenca");
        long imageId = insertImage(id, "a", "survey_cutout", false);
        assertThatThrownBy(() -> jdbc.sql("UPDATE object_image SET license_code = 'NASA-PD' WHERE id = :id")
                .param("id", imageId).update()).isInstanceOf(DataIntegrityViolationException.class);
    }
}
