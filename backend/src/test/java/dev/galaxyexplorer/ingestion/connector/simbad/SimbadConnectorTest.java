package dev.galaxyexplorer.ingestion.connector.simbad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.ingestion.connector.FetchedBatch;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.Resolution;
import dev.galaxyexplorer.ingestion.connector.ResolvedName;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.support.FixtureSesameClient;
import dev.galaxyexplorer.support.FixtureTapClient;

/** Conector real sobre respostas REAIS gravadas (Sesame + TAP). */
class SimbadConnectorTest {

    private final FixtureTapClient tap = new FixtureTapClient();
    private final FixtureSesameClient sesame = new FixtureSesameClient();
    private final SimbadConnector connector = SimbadTestSupport.connector(tap, sesame);

    @Test
    void resolvesThroughSesameInOneRequest() {
        Resolution r = connector.resolve(SimbadTestSupport.SAMPLE_QUERIES);

        assertThat(sesame.callCount()).isEqualTo(1);
        assertThat(r.resolved()).hasSize(11);
        assertThat(r.unresolved()).singleElement().satisfies(u -> {
            assertThat(u.query()).isEqualTo("XyzzyNotAnObject123");
            assertThat(u.reason()).isEqualTo(SimbadConnector.NOT_FOUND);
        });
        ResolvedName m45 = r.resolved().stream().filter(n -> n.queries().contains("M45")).findFirst().orElseThrow();
        assertThat(m45.ref().sourceInternalId()).isEqualTo(675533L);
        assertThat(m45.ref().sourceRef()).isEqualTo("675533");
        assertThat(r.raw().dataset()).isEqualTo("sesame");
        assertThat(r.raw().format()).isEqualTo(RawPayload.BodyFormat.TEXT);
        assertThat(r.raw().requestUrl()).startsWith("https://cds.unistra.fr/cgi-bin/nph-sesame/-oxp/S?M31&M1&");
    }

    @Test
    void controlCharactersNeverReachTheResolver() {
        assertThatThrownBy(() -> connector.resolve(List.of("M31\n")))
                .isInstanceOf(SourceQueryException.class);
        assertThat(sesame.callCount()).isZero();
    }

    @Test
    void fetchRunsFourTapQueriesByOid() {
        Resolution r = connector.resolve(SimbadTestSupport.SAMPLE_QUERIES);
        FetchedBatch batch = connector.fetch(r.resolved());

        assertThat(tap.callCount()).isEqualTo(4);
        assertThat(batch.payloadsByDataset()).containsOnlyKeys("basic", "ident", "flux", "mesDistance");
        assertThat(batch.payloadsByDataset().get("basic").queryText()).contains("WHERE oid IN (433918, 675533,");
    }

    @Test
    void schemaCheckAgainstRealTapSchema() {
        assertThat(connector.checkSchema().ok()).isTrue();
        assertThat(tap.callCount()).isEqualTo(1);
    }
}
