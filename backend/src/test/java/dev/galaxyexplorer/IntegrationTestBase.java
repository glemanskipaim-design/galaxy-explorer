package dev.galaxyexplorer;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;

import dev.galaxyexplorer.ingestion.connector.simbad.SesameClient;
import dev.galaxyexplorer.ingestion.connector.tap.TapClient;
import dev.galaxyexplorer.support.FixtureSesameClient;
import dev.galaxyexplorer.support.FixtureTapClient;

/**
 * Base dos testes de integracao: PostgreSQL real (Testcontainers, postgres:17),
 * clientes do CDS trocados pelos de fixture (sem rede). Todas as subclasses
 * compartilham UM contexto Spring (mesma configuracao) — um container so.
 *
 * <p>Sem Docker o teste FALHA (nada de {@code disabledWithoutDocker}): o gate
 * nunca fica verde sem testar o banco.
 */
@SpringBootTest(properties = "galaxy.ingestion.batch-size=20")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    @TestBean(name = "simbadTapClient", methodName = "dev.galaxyexplorer.IntegrationTestBase#fixtureTapClient")
    protected TapClient tapClient;

    @TestBean(name = "simbadSesameClient",
            methodName = "dev.galaxyexplorer.IntegrationTestBase#fixtureSesameClient")
    protected SesameClient sesameClient;

    @Autowired
    protected JdbcClient jdbc;

    static TapClient fixtureTapClient() {
        return new FixtureTapClient();
    }

    static SesameClient fixtureSesameClient() {
        return new FixtureSesameClient();
    }

    protected FixtureTapClient fixtureTap() {
        return (FixtureTapClient) tapClient;
    }

    protected FixtureSesameClient fixtureSesame() {
        return (FixtureSesameClient) sesameClient;
    }

    /** Limpa dados (nunca os de referencia da V3) e zera os contadores dos clientes de fixture. */
    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("TRUNCATE celestial_object, ingestion_run, bib_reference, import_job RESTART IDENTITY CASCADE")
                .update();
        fixtureTap().reset();
        fixtureSesame().reset();
    }

    protected int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }
}
