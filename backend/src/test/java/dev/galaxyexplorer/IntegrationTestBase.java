package dev.galaxyexplorer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

import dev.galaxyexplorer.ingestion.connector.esa.EsaOutreachClient;
import dev.galaxyexplorer.ingestion.connector.hips2fits.Hips2FitsClient;
import dev.galaxyexplorer.ingestion.connector.image.ImageDownloader;
import dev.galaxyexplorer.ingestion.connector.simbad.SesameClient;
import dev.galaxyexplorer.ingestion.connector.tap.TapClient;
import dev.galaxyexplorer.support.FixtureEsaClient;
import dev.galaxyexplorer.support.FixtureHips2FitsClient;
import dev.galaxyexplorer.support.FixtureSesameClient;
import dev.galaxyexplorer.support.FixtureTapClient;

/**
 * Base dos testes de integracao: PostgreSQL real (Testcontainers, postgres:17),
 * clientes do CDS e das fontes de imagem trocados pelos de fixture (sem rede); arquivos de
 * imagem num diretorio temporario. Todas as subclasses
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

    @TestBean(name = "hips2FitsClient", methodName = "dev.galaxyexplorer.IntegrationTestBase#fixtureHips2Fits")
    protected Hips2FitsClient hips2FitsClient;

    @TestBean(name = "esaOutreachClient", methodName = "dev.galaxyexplorer.IntegrationTestBase#fixtureEsa")
    protected EsaOutreachClient esaClient;

    @TestBean(name = "esaImageDownloader", methodName = "dev.galaxyexplorer.IntegrationTestBase#fixtureEsaDownloader")
    protected ImageDownloader esaDownloader;

    @Autowired
    protected JdbcClient jdbc;

    /** Uma instancia so serve busca/AVM e download ESA (o contexto e compartilhado). */
    private static final FixtureEsaClient ESA = new FixtureEsaClient();
    private static final Path STORAGE;

    static {
        try {
            STORAGE = Files.createTempDirectory("galaxy-images-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void imageStorage(DynamicPropertyRegistry registry) {
        registry.add("galaxy.media.storage-dir", STORAGE::toString);
    }

    static Hips2FitsClient fixtureHips2Fits() {
        return new FixtureHips2FitsClient();
    }

    static EsaOutreachClient fixtureEsa() {
        return ESA;
    }

    /** Lambda, nao a propria instancia: senao o bean tambem seria um {@code EsaOutreachClient}. */
    static ImageDownloader fixtureEsaDownloader() {
        return ESA::download;
    }

    protected FixtureHips2FitsClient fixtureCutouts() {
        return (FixtureHips2FitsClient) hips2FitsClient;
    }

    protected FixtureEsaClient fixtureEsaClient() {
        return ESA;
    }

    protected static Path imageStorage() {
        return STORAGE;
    }

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
        fixtureCutouts().reset();
        ESA.reset();
    }

    protected int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }
}
