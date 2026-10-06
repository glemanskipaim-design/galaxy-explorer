package dev.galaxyexplorer.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.IntegrationTestBase;

class MigrationIntegrationTest extends IntegrationTestBase {

    @Test
    void allMigrationsApplied() {
        List<String> versions = jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
                .query(String.class).list();
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    void schemaTablesExist() {
        List<String> tables = jdbc.sql("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
                """).query(String.class).list();
        assertThat(tables).contains("source", "ingestion_run", "raw_payload", "bib_reference", "object_category",
                "source_type_mapping", "celestial_object", "object_source_type", "object_identifier",
                "property_definition", "measurement", "property_source_priority", "object_position",
                "orbit_solution", "object_image", "import_job");
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
}
