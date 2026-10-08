package dev.galaxyexplorer.ingestion.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import dev.galaxyexplorer.catalog.read.MatchType;
import dev.galaxyexplorer.catalog.read.SearchHit;
import dev.galaxyexplorer.catalog.read.SearchService;
import dev.galaxyexplorer.ingestion.images.ImageIngestionService;
import dev.galaxyexplorer.ingestion.pipeline.IngestionPipeline;
import dev.galaxyexplorer.ingestion.pipeline.IngestionReport;
import dev.galaxyexplorer.ingestion.pipeline.ObjectOutcome;
import dev.galaxyexplorer.ingestion.pipeline.ObjectOutcome.Kind;
import dev.galaxyexplorer.ingestion.pipeline.RunStatus;

class ImportJobWorkerTest {

    private final ImportJobRepository jobs = mock(ImportJobRepository.class);
    private final SearchService search = mock(SearchService.class);
    private final IngestionPipeline pipeline = mock(IngestionPipeline.class);
    private final ImageIngestionService images = mock(ImageIngestionService.class);
    private final ImportJobWorker worker = new ImportJobWorker(jobs, search, pipeline, images);

    private void givenQueuedJob(long id, String query) {
        when(jobs.findById(id)).thenReturn(Optional.of(new ImportJob(id, query, query.toLowerCase(),
                ImportJobStatus.QUEUED, null, null, List.of(), Instant.EPOCH, null, null)));
        when(search.search(any(), anyInt())).thenReturn(List.of());
    }

    @Test
    void exceptionEscapingThePipelineEndsFailedNotStuckRunning() {
        givenQueuedJob(7, "Vega");
        when(pipeline.run(any())).thenThrow(new IllegalStateException("SQL [DELETE ...] detalhe interno"));

        worker.execute(7);

        verify(jobs).markRunning(7);
        verify(jobs).markFinished(eq(7L), eq(ImportJobStatus.FAILED), eq("Erro interno."), isNull(), eq(List.of()));
    }

    @Test
    void sourceFailureCategoryBecomesTheUserMessage() {
        givenQueuedJob(8, "Vega");
        when(pipeline.run(any())).thenReturn(new IngestionReport(42, RunStatus.FAILED, 0, 1,
                List.of(ObjectOutcome.failed("Vega", Kind.SOURCE_QUERY_FAILED, "TAP recusou: <corpo da fonte>")),
                List.of()));

        worker.execute(8);

        verify(jobs).markFinished(eq(8L), eq(ImportJobStatus.FAILED), eq("Falha na consulta à fonte (run 42)."),
                eq(42L), eq(List.of()));
    }

    @Test
    void everyFailureCategoryHasItsOwnUserMessageWithoutDetail() {
        List<Kind> failures = List.of(Kind.SOURCE_UNAVAILABLE, Kind.SOURCE_QUERY_FAILED, Kind.SCHEMA_MISMATCH,
                Kind.REJECTED, Kind.WRITE_FAILED, Kind.INTERNAL_ERROR);
        for (Kind kind : failures) {
            assertThat(ImportJobWorker.userMessage(kind, 3L)).endsWith("(run 3).");
        }
        assertThat(failures.stream().map(k -> ImportJobWorker.userMessage(k, null)).distinct()).hasSize(6);
        assertThat(ImportJobWorker.userMessage(Kind.WRITE_FAILED, 3L)).isEqualTo("Falha ao gravar no catálogo (run 3).");
        assertThat(ImportJobWorker.userMessage(Kind.SOURCE_UNAVAILABLE, null))
                .isEqualTo("Fonte indisponível no momento (SIMBAD/CDS); tente novamente mais tarde.");
    }

    @Test
    void newObjectGetsItsCutoutBeforeTheJobFinishes() {
        givenQueuedJob(9, "Rigel");
        UUID id = UUID.randomUUID();
        when(pipeline.run(any())).thenReturn(new IngestionReport(43, RunStatus.OK, 1, 0,
                List.of(ObjectOutcome.written("Rigel", id, "rigel", true)), List.of()));

        worker.execute(9);

        InOrder order = inOrder(images, jobs);
        order.verify(images).ingestCutoutsAfterImport(List.of(id));
        order.verify(jobs).markFinished(eq(9L), eq(ImportJobStatus.SUCCEEDED), isNull(), eq(43L), eq(List.of(id)));
    }

    @Test
    void cutoutFailureNeverChangesTheJobStatus() {
        givenQueuedJob(10, "Rigel");
        UUID id = UUID.randomUUID();
        when(pipeline.run(any())).thenReturn(new IngestionReport(44, RunStatus.OK, 1, 0,
                List.of(ObjectOutcome.written("Rigel", id, "rigel", true)), List.of()));
        doThrow(new IllegalStateException("CDS fora")).when(images).ingestCutoutsAfterImport(any());

        worker.execute(10);

        verify(jobs).markFinished(eq(10L), eq(ImportJobStatus.SUCCEEDED), isNull(), eq(44L), eq(List.of(id)));
    }

    @Test
    void alreadyInCatalogDoesNotCallTheCutout() {
        when(jobs.findById(11L)).thenReturn(Optional.of(new ImportJob(11, "M51", "m51", ImportJobStatus.QUEUED, null,
                null, List.of(), Instant.EPOCH, null, null)));
        when(search.search(any(), anyInt())).thenReturn(List.of(new SearchHit(UUID.randomUUID(), "messier-51",
                "M 51", null, null, "M  51", MatchType.EXACT, 1.0, null)));

        worker.execute(11);

        verifyNoInteractions(images, pipeline);
    }

    @Test
    void successAndNotFoundAreNotFailureMessages() {
        assertThatThrownBy(() -> ImportJobWorker.userMessage(Kind.WRITTEN, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImportJobWorker.userMessage(Kind.NOT_FOUND, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
