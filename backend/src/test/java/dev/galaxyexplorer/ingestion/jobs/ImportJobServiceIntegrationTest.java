package dev.galaxyexplorer.ingestion.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.ingestion.pipeline.IngestionPipeline;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRequest;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;
import dev.galaxyexplorer.support.Samples;

class ImportJobServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    ImportJobService service;
    @Autowired
    ImportJobRepository jobs;
    @Autowired
    IngestionPipeline pipeline;

    private ImportJob awaitFinished(long id) {
        await().atMost(Duration.ofSeconds(30)).until(() -> !jobs.findById(id).orElseThrow().status().active());
        return jobs.findById(id).orElseThrow();
    }

    @Test
    void termAlreadyInCatalogSucceedsWithoutCallingSource() {
        pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.SEED, Samples.QUERIES));
        fixtureTap().reset();
        fixtureSesame().reset();

        ImportJob job = awaitFinished(service.submit("  m 31 ").id());

        assertThat(job.status()).isEqualTo(ImportJobStatus.SUCCEEDED);
        assertThat(job.message()).isEqualTo(ImportJobWorker.ALREADY_IN_CATALOG);
        assertThat(job.resultObjectIds()).hasSize(1);
        assertThat(job.query()).isEqualTo("m 31");
        assertThat(fixtureTap().callCount()).isZero();
        assertThat(fixtureSesame().callCount()).isZero();
    }

    @Test
    void unknownNameEndsNotFound() {
        ImportJob job = awaitFinished(service.submit(Samples.NOT_FOUND_QUERY).id());

        assertThat(job.status()).isEqualTo(ImportJobStatus.NOT_FOUND);
        assertThat(job.message()).isEqualTo("Objeto não encontrado em SIMBAD");
        assertThat(job.runId()).isNotNull();
        assertThat(job.resultObjectIds()).isEmpty();
    }

    @Test
    void activeJobForSameTermIsReused() {
        ImportJob queued = jobs.insertQueued("Vega", "vega");

        ImportJob again = service.submit(" VEGA ");

        assertThat(again.id()).isEqualTo(queued.id());
        assertThat(count("import_job")).isEqualTo(1);
    }

    @Test
    void jobsLeftRunningByRestartBecomeFailed() {
        ImportJob job = jobs.insertQueued("Vega", "vega");
        jobs.markRunning(job.id());

        service.recoverInterrupted();

        ImportJob after = jobs.findById(job.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(after.message()).isEqualTo(ImportJobService.INTERRUPTED);
        assertThat(after.finishedAt()).isNotNull();
    }
}
