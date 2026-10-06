package dev.galaxyexplorer.ingestion.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.Epistemic;
import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.catalog.measurement.MeasurementRepository;
import dev.galaxyexplorer.catalog.measurement.MeasurementWrite;
import dev.galaxyexplorer.catalog.object.CelestialObjectRepository;
import dev.galaxyexplorer.catalog.source.SourceRepository;
import dev.galaxyexplorer.ingestion.pipeline.IngestionPipeline;
import dev.galaxyexplorer.ingestion.pipeline.IngestionReport;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRequest;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRunRepository;
import dev.galaxyexplorer.ingestion.pipeline.RawPayloadRepository;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;
import dev.galaxyexplorer.support.Fixtures;

class ImportRetentionIntegrationTest extends IntegrationTestBase {

    @Autowired
    ImportRetention retention;
    @Autowired
    IngestionPipeline pipeline;
    @Autowired
    IngestionRunRepository runs;
    @Autowired
    RawPayloadRepository rawPayloads;
    @Autowired
    SourceRepository sources;
    @Autowired
    CelestialObjectRepository objects;
    @Autowired
    MeasurementRepository measurements;

    private static OffsetDateTime daysAgo(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).minus(Duration.ofDays(days));
    }

    private void insertJob(String query, String status, OffsetDateTime createdAt) {
        jdbc.sql("INSERT INTO import_job (query, normalized_query, status, created_at) VALUES (:q, :q, :s, :c)")
                .param("q", query).param("s", status).param("c", createdAt).update();
    }

    private void age(long runId, int days) {
        jdbc.sql("UPDATE ingestion_run SET finished_at = :t WHERE id = :id").param("t", daysAgo(days))
                .param("id", runId).update();
    }

    @Test
    void purgesOldFinishedJobsButNeverActiveOrRecentOnes() {
        insertJob("antigo-ok", "succeeded", daysAgo(40));
        insertJob("antigo-falhou", "failed", daysAgo(40));
        insertJob("antigo-ativo", "running", daysAgo(40));
        insertJob("recente", "not_found", daysAgo(2));

        retention.purgeExpired();

        assertThat(jdbc.sql("SELECT query FROM import_job ORDER BY query").query(String.class).list())
                .containsExactly("antigo-ativo", "recente");
    }

    @Test
    void purgesRawPayloadsOnlyOfOldUnproductiveOnDemandRuns() {
        // nome inexistente (fixture real not_found): run on_demand com 0 objetos e 2 payloads crus
        IngestionReport miss = pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.ON_DEMAND,
                List.of("XyzzyNotAnObject123")));
        // run on_demand que gravou um objeto (Vega): seus payloads sao proveniencia e ficam
        IngestionReport hit = pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.ON_DEMAND,
                List.of("Vega")));
        age(miss.runId(), 40);
        age(hit.runId(), 40);
        int hitPayloads = jdbc.sql("SELECT count(*) FROM raw_payload WHERE run_id = :id").param("id", hit.runId())
                .query(Integer.class).single();

        retention.purgeExpired();

        assertThat(jdbc.sql("SELECT count(*) FROM raw_payload WHERE run_id = :id").param("id", miss.runId())
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM raw_payload WHERE run_id = :id").param("id", hit.runId())
                .query(Integer.class).single()).isEqualTo(hitPayloads).isPositive();
        assertThat(count("ingestion_run")).isEqualTo(2); // o registro do run fica
    }

    @Test
    void interruptedRunWithZeroOkKeepsPayloadsStillReferencedByMeasurements() {
        // run on_demand interrompido: gravou uma medicao, mas failDangling o fecha com records_ok = 0
        short simbad = sources.getByCode(SourceCode.SIMBAD).id();
        long runId = runs.open(simbad, RunTrigger.ON_DEMAND, "test");
        long referenced = rawPayloads.insert(runId, Fixtures.payload("basic", "vega_basic"));
        long orphan = rawPayloads.insert(runId, Fixtures.payload("ident", "vega_ident"));
        UUID vega = objects.insert("vega", "Vega", null, null);
        measurements.upsert(vega, simbad, MeasurementWrite.builder("parallax", Epistemic.OBSERVED, "basic",
                        "https://simbad.cds.unistra.fr/simbad/sim-id?Ident=%2A%20alf%20Lyr", Fixtures.CAPTURED_AT)
                .value(130.23)
                .errors(0.36, 0.36)
                .original("130.23", "mas")
                .originalError("0.36")
                .rawPayloadId(referenced)
                .build());
        runs.failDangling("interrompido por reinício do servidor");
        age(runId, 40);
        assertThat(jdbc.sql("SELECT records_ok FROM ingestion_run WHERE id = :id").param("id", runId)
                .query(Integer.class).single()).isZero();

        retention.purgeExpired(); // nao pode lancar (FK de measurement.raw_payload_id)

        List<Long> left = jdbc.sql("SELECT id FROM raw_payload WHERE run_id = :id ORDER BY id").param("id", runId)
                .query(Long.class).list();
        assertThat(left).containsExactly(referenced).doesNotContain(orphan);
        assertThat(count("measurement")).isEqualTo(1);
    }
}
