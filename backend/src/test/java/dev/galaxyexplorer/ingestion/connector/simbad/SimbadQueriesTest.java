package dev.galaxyexplorer.ingestion.connector.simbad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.support.Fixtures;

/**
 * Cada builder gera exatamente a ADQL gravada ao lado da fixture — garante que
 * fixture e codigo nao divergiram.
 */
class SimbadQueriesTest {

    /** oids reais devolvidos pelo Sesame em fixtures/sesame/sample_resolve.xml, fora de ordem de proposito. */
    private static final List<Long> SAMPLE_OIDS = List.of(1575544L, 795871L, 810146L, 675533L, 1937204L, 2894585L,
            2868878L, 433918L, 843879L, 2419046L, 2395133L);

    private static String fixture(String name) {
        return norm(Fixtures.read("simbad/" + name + ".adql"));
    }

    private static String norm(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    @Test
    void schemaCheck() {
        assertThat(norm(SimbadQueries.schemaCheck())).isEqualTo(fixture("tap_schema"));
    }

    @Test
    void fetchQueriesMatchFixturesWithSortedOids() {
        assertThat(norm(SimbadQueries.basic(SAMPLE_OIDS))).isEqualTo(fixture("sample_basic"));
        assertThat(norm(SimbadQueries.identifiers(SAMPLE_OIDS))).isEqualTo(fixture("sample_ident"));
        assertThat(norm(SimbadQueries.fluxes(SAMPLE_OIDS))).isEqualTo(fixture("sample_flux"));
        assertThat(norm(SimbadQueries.distances(SAMPLE_OIDS))).isEqualTo(fixture("sample_mesdistance"));
    }

    @Test
    void czFixturesMatchToo() {
        List<Long> m51m81m94 = List.of(587337L, 1976412L, 434630L);
        assertThat(norm(SimbadQueries.basic(m51m81m94))).isEqualTo(fixture("cz_basic"));
        assertThat(norm(SimbadQueries.identifiers(m51m81m94))).isEqualTo(fixture("cz_ident"));
        assertThat(norm(SimbadQueries.fluxes(m51m81m94))).isEqualTo(fixture("cz_flux"));
        assertThat(norm(SimbadQueries.distances(m51m81m94))).isEqualTo(fixture("cz_mesdistance"));
    }

    @Test
    void queriesAreSingleLine() {
        assertThat(SimbadQueries.basic(SAMPLE_OIDS)).doesNotContain("\n");
        assertThat(SimbadQueries.schemaCheck()).doesNotContain("\n");
    }
}
