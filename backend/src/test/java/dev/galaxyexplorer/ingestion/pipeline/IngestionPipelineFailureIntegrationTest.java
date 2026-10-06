package dev.galaxyexplorer.ingestion.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.catalog.category.CategoryRepository;
import dev.galaxyexplorer.catalog.category.CategoryResolver;
import dev.galaxyexplorer.catalog.derived.DerivedValueService;
import dev.galaxyexplorer.catalog.identifier.IdentifierRepository;
import dev.galaxyexplorer.catalog.measurement.MeasurementRepository;
import dev.galaxyexplorer.catalog.measurement.PreferenceService;
import dev.galaxyexplorer.catalog.object.CelestialObjectRepository;
import dev.galaxyexplorer.catalog.object.ObjectSourceTypeRepository;
import dev.galaxyexplorer.catalog.position.PositionRepository;
import dev.galaxyexplorer.catalog.reference.BibReferenceRepository;
import dev.galaxyexplorer.catalog.source.SourceRepository;
import dev.galaxyexplorer.catalog.write.CatalogWriter;
import dev.galaxyexplorer.catalog.write.ObjectWrite;
import dev.galaxyexplorer.catalog.write.WriteResult;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import dev.galaxyexplorer.ingestion.connector.simbad.SimbadConnector;
import dev.galaxyexplorer.ingestion.connector.simbad.SimbadProperties;
import dev.galaxyexplorer.ingestion.pipeline.ObjectOutcome.Kind;
import dev.galaxyexplorer.support.FixtureSesameClient;
import dev.galaxyexplorer.support.FixtureTapClient;

/**
 * Ramos de falha do pipeline, sobre respostas REAIS gravadas e banco real. O
 * pipeline e montado a mao para variar batch-size, MAXREC e o CatalogWriter sem
 * criar outro contexto Spring. Falhas sao injetadas nos clientes de fixture
 * (excecao na N-esima chamada / corpo real alterado em memoria) — nunca dado inventado.
 *
 * <p>Fixtures por lote: {@code M51,M81,M94} (cz_*), {@code Vega}, {@code TRAPPIST-1},
 * {@code XyzzyNotAnObject123} (not_found).
 */
class IngestionPipelineFailureIntegrationTest extends IntegrationTestBase {

    private static final List<String> CZ = List.of("M51", "M81", "M94");

    @Autowired
    IngestionRunRepository runs;
    @Autowired
    RawPayloadRepository rawPayloads;
    @Autowired
    SourceRepository sources;
    @Autowired
    CatalogWriter writer;
    @Autowired
    TransactionTemplate tx;
    // dependencias para montar um CatalogWriter que falha em um objeto
    @Autowired
    ObjectSourceTypeRepository sourceTypes;
    @Autowired
    CelestialObjectRepository objects;
    @Autowired
    CategoryRepository categories;
    @Autowired
    CategoryResolver categoryResolver;
    @Autowired
    IdentifierRepository identifiers;
    @Autowired
    BibReferenceRepository references;
    @Autowired
    PositionRepository positions;
    @Autowired
    MeasurementRepository measurements;
    @Autowired
    DerivedValueService derived;
    @Autowired
    PreferenceService preferences;

    private FixtureTapClient tap;
    private FixtureSesameClient sesame;

    @BeforeEach
    void freshClients() {
        tap = new FixtureTapClient();
        sesame = new FixtureSesameClient();
    }

    private static SimbadProperties props(int maxRec) {
        return new SimbadProperties(URI.create("https://simbad.cds.unistra.fr/simbad/sim-tap/sync"),
                FixtureSesameClient.SESAME_URL, 2, Duration.ofSeconds(10), Duration.ofSeconds(60), maxRec, 3,
                Duration.ofSeconds(1), "GalaxyExplorer/test");
    }

    private IngestionPipeline pipeline(int batchSize, int maxRec, CatalogWriter catalogWriter) {
        SimbadConnector connector = new SimbadConnector(tap, sesame, props(maxRec));
        return new IngestionPipeline(List.of(connector), runs, rawPayloads, sources, catalogWriter, tx,
                new IngestionProperties(batchSize));
    }

    private IngestionReport run(IngestionPipeline p, List<String> queries) {
        return p.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.MANUAL, queries));
    }

    private Map<String, Object> runRow(long runId) {
        return jdbc.sql("SELECT status, records_ok, records_failed, error_summary FROM ingestion_run WHERE id = :id")
                .param("id", runId).query().singleRow();
    }

    private static ObjectOutcome outcome(IngestionReport r, String query) {
        return r.outcomes().stream().filter(o -> o.query().equals(query)).findFirst().orElseThrow();
    }

    @Test
    void writeFailureRollsBackOnlyThatObjectAndRunContinues() {
        // escreve M51 por inteiro e entao explode: a transacao do objeto precisa desfazer tudo
        CatalogWriter failingOnM51 = new CatalogWriter(sources, sourceTypes, objects, categories, categoryResolver,
                identifiers, references, positions, measurements, derived, preferences) {
            @Override
            public WriteResult write(ObjectWrite w) {
                WriteResult r = super.write(w);
                if (w.sourceRef().equals("587337")) {
                    throw new IllegalStateException("falha simulada depois de gravar M51");
                }
                return r;
            }
        };

        IngestionReport report = run(pipeline(10, 100000, failingOnM51), CZ);

        assertThat(report.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(outcome(report, "M51").kind()).isEqualTo(Kind.WRITE_FAILED);
        assertThat(outcome(report, "M81").ok()).isTrue();
        assertThat(outcome(report, "M94").ok()).isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM object_source_type WHERE source_ref = '587337'")
                .query(Integer.class).single()).isZero();
        assertThat(count("celestial_object")).isEqualTo(2);
        assertThat((String) runRow(report.runId()).get("error_summary")).contains("M51: falha ao gravar");
    }

    @Test
    void queryFailureInOneBatchDoesNotStopNextBatches_realTruncation() {
        // MAXREC=20: ident de Vega tem 65 linhas (truncado); TRAPPIST-1 tem no maximo 16 por consulta
        IngestionReport report = run(pipeline(1, 20, writer), List.of("Vega", "TRAPPIST-1"));

        assertThat(report.status()).isEqualTo(RunStatus.PARTIAL);
        ObjectOutcome vega = outcome(report, "Vega");
        assertThat(vega.kind()).isEqualTo(Kind.SOURCE_QUERY_FAILED);
        assertThat(vega.failureReason()).contains("truncado em MAXREC=20");
        assertThat(outcome(report, "TRAPPIST-1").ok()).isTrue();
        assertThat(sesame.callCount()).isEqualTo(2);
    }

    @Test
    void sourceUnavailableMidRunFailsAllRemainingBatches() {
        sesame.failOnCall(2, () -> new SourceUnavailableException("circuito aberto (simulado)"));

        IngestionReport report = run(pipeline(1, 100000, writer), List.of("Vega", "TRAPPIST-1",
                "XyzzyNotAnObject123"));

        assertThat(report.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(outcome(report, "Vega").ok()).isTrue();
        assertThat(outcome(report, "TRAPPIST-1").kind()).isEqualTo(Kind.SOURCE_UNAVAILABLE);
        assertThat(outcome(report, "XyzzyNotAnObject123").kind()).isEqualTo(Kind.SOURCE_UNAVAILABLE);
        assertThat(sesame.callCount()).isEqualTo(2); // o 3o lote nem foi tentado
        assertThat(report.recordsOk()).isEqualTo(1);
        assertThat(report.recordsFailed()).isEqualTo(2);
    }

    @Test
    void schemaMismatchFailsRunWithoutTouchingCatalog() {
        tap.transformOn("tap_schema", body -> body.replace("[\"basic\",\"plx_value\",\"mas\",\"DOUBLE\"]",
                "[\"basic\",\"plx_value\",\"arcsec\",\"DOUBLE\"]"));

        IngestionReport report = run(pipeline(10, 100000, writer), CZ);

        assertThat(report.status()).isEqualTo(RunStatus.FAILED);
        assertThat(report.outcomes()).hasSize(3).allMatch(o -> o.kind() == Kind.SCHEMA_MISMATCH);
        assertThat(sesame.callCount()).isZero();
        assertThat(count("celestial_object")).isZero();
        assertThat(count("raw_payload")).isEqualTo(1); // so a resposta do TAP_SCHEMA, para auditoria
        assertThat((String) runRow(report.runId()).get("error_summary"))
                .contains("UNIT_MISMATCH basic.plx_value: esperado mas, veio arcsec");
    }

    @Test
    void rejectedBatchGoesToErrorSummaryAndZeroOkIsFailed() {
        tap.transformOn("basic", body -> body.replace(
                "{\"name\":\"plx_value\",\"description\":\"Parallax\",\"datatype\":\"DOUBLE\",\"unit\":\"mas\"",
                "{\"name\":\"plx_value\",\"description\":\"Parallax\",\"datatype\":\"DOUBLE\",\"unit\":\"arcsec\""));

        IngestionReport report = run(pipeline(10, 100000, writer), CZ);

        assertThat(report.status()).isEqualTo(RunStatus.FAILED);
        assertThat(report.recordsOk()).isZero();
        assertThat(report.outcomes()).allMatch(o -> o.kind() == Kind.REJECTED);
        Map<String, Object> row = runRow(report.runId());
        assertThat(row.get("status")).isEqualTo("failed");
        assertThat((String) row.get("error_summary")).contains("M51: rejeitado na validação")
                .contains("UNIT_MISMATCH basic.plx_value");
        assertThat(count("celestial_object")).isZero();
    }

    @Test
    void errorSummaryIsTruncatedAt4000Characters() {
        // a mensagem longa e nossa (excecao simulada), nao dado de fonte
        sesame.failOnCall(1, () -> new SourceQueryException("x".repeat(6000)));

        IngestionReport report = run(pipeline(10, 100000, writer), CZ);

        assertThat(report.status()).isEqualTo(RunStatus.FAILED);
        String summary = (String) runRow(report.runId()).get("error_summary");
        assertThat(summary).hasSize(IngestionPipeline.MAX_ERROR_SUMMARY).endsWith("...");
    }
}
