package dev.galaxyexplorer.ingestion.connector.tap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.support.Fixtures;

/** Sobre a resposta REAL gravada {@code sample_basic.json}. */
class TapJsonParserTest {

    private final TapResult basic = TapJsonParser.parse(Fixtures.read("simbad/sample_basic.json"));

    private TapRow row(long oid) {
        return basic.rows().stream().filter(r -> r.integer("oid").orElseThrow() == oid).findFirst().orElseThrow();
    }

    @Test
    void columnsAndUnitsFromMetadata() {
        assertThat(basic.columns()).hasSize(21);
        assertThat(basic.column("ra").orElseThrow().unit()).isEqualTo("deg");
        assertThat(basic.column("PLX_VALUE").orElseThrow().unit()).isEqualTo("mas");
        assertThat(basic.column("rvz_radvel").orElseThrow().unit()).isEqualTo("km.s-1");
        assertThat(basic.column("main_id").orElseThrow().unit()).isNull();
        assertThat(basic.size()).isEqualTo(11);
    }

    @Test
    void valuesKeepExactJsonSpelling() {
        TapRow m31 = row(1575544);
        assertThat(m31.text("main_id")).contains("M  31");
        assertThat(m31.text("morph_type")).contains("SA(s)b");
        assertThat(m31.rawText("rvz_radvel")).contains("-300");
        assertThat(m31.number("rvz_radvel")).contains(-300.0);
        TapRow m82 = row(433918);
        assertThat(m82.rawText("rvz_err")).contains("9.0E-5");
        TapRow m1 = row(795871);
        assertThat(m1.rawText("galdim_majaxis")).contains("7"); // inteiro no JSON; double seria "7.0"
    }

    @Test
    void nullsAreEmpty() {
        TapRow sgrA = row(2419046);
        assertThat(sgrA.number("plx_value")).isEmpty();
        assertThat(sgrA.rawText("plx_value")).isEmpty();
        assertThat(sgrA.text("morph_type")).isEmpty();
    }

    @Test
    void unknownColumnIsProgrammingError() {
        assertThatThrownBy(() -> row(1575544).text("nao_existe")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notTapJsonIsRejected() {
        assertThatThrownBy(() -> TapJsonParser.parse(Fixtures.read("simbad/error_bad_adql.xml")))
                .isInstanceOf(SourceQueryException.class);
        assertThatThrownBy(() -> TapJsonParser.parse("{\"data\":[]}")).isInstanceOf(SourceQueryException.class)
                .hasMessageContaining("metadata");
    }
}
