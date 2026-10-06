package dev.galaxyexplorer.ingestion.connector.simbad;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import dev.galaxyexplorer.support.FixtureSesameClient;
import dev.galaxyexplorer.support.FixtureTapClient;

/** Monta o conector real sobre os clientes de fixture (sem rede, sem Spring). */
final class SimbadTestSupport {

    /** Exatamente os nomes de fixtures/sesame/sample_resolve.names (11 reais + 1 inexistente). */
    static final List<String> SAMPLE_QUERIES = List.of("M31", "M1", "M42", "M45", "M87", "M13", "M57", "M82",
            "Betelgeuse", "Sgr A*", "M54", "XyzzyNotAnObject123");

    static final long M31 = 1575544L;
    static final long M82 = 433918L;
    static final long M87 = 1937204L;
    static final long BETELGEUSE = 843879L;
    static final long SGR_A_STAR = 2419046L;
    static final long M54 = 2395133L;

    private SimbadTestSupport() {
    }

    static SimbadProperties properties() {
        return new SimbadProperties(URI.create("https://simbad.cds.unistra.fr/simbad/sim-tap/sync"),
                FixtureSesameClient.SESAME_URL, 2, Duration.ofSeconds(10), Duration.ofSeconds(60), 100000, 3,
                Duration.ofSeconds(1), "GalaxyExplorer/test");
    }

    static SimbadConnector connector(FixtureTapClient tap, FixtureSesameClient sesame) {
        return new SimbadConnector(tap, sesame, properties());
    }
}
