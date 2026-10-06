package dev.galaxyexplorer.ingestion.connector.simbad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.SchemaCheck;
import dev.galaxyexplorer.ingestion.connector.tap.TapJsonParser;
import dev.galaxyexplorer.support.Fixtures;

class SimbadSchemaExpectationsTest {

    private static RawPayload schemaPayload(String body) {
        return RawPayload.of("tap_schema", Fixtures.SIMBAD_TAP_URL, SimbadQueries.schemaCheck(), body,
                RawPayload.BodyFormat.JSON, Fixtures.CAPTURED_AT);
    }

    @Test
    void realTapSchemaPasses() {
        SchemaCheck check = SimbadSchemaExpectations.evaluate(schemaPayload(Fixtures.read("simbad/tap_schema.json")));
        assertThat(check.problems()).isEmpty();
        assertThat(check.ok()).isTrue();
    }

    @Test
    void sameRealPayloadWithUnitChangedInMemoryFails() {
        String real = Fixtures.read("simbad/tap_schema.json");
        String tampered = real.replace("[\"basic\",\"plx_value\",\"mas\",\"DOUBLE\"]",
                "[\"basic\",\"plx_value\",\"arcsec\",\"DOUBLE\"]");
        assertThat(tampered).isNotEqualTo(real); // o trecho existe na fixture real

        SchemaCheck check = SimbadSchemaExpectations.evaluate(schemaPayload(tampered));
        assertThat(check.ok()).isFalse();
        assertThat(check.problems()).containsExactly("UNIT_MISMATCH basic.plx_value: esperado mas, veio arcsec");
    }

    @Test
    void missingColumnIsReported() {
        String tampered = Fixtures.read("simbad/tap_schema.json").replace("\"galdim_bibcode\"", "\"galdim_bibcodeX\"");
        SchemaCheck check = SimbadSchemaExpectations.evaluate(schemaPayload(tampered));
        assertThat(check.problems()).containsExactly("SCHEMA_MISSING basic.galdim_bibcode");
    }

    @Test
    void unitsOfRealResponseMatch() {
        assertThat(SimbadSchemaExpectations.unitProblems("basic",
                TapJsonParser.parse(Fixtures.read("simbad/sample_basic.json")))).isEmpty();
    }
}
