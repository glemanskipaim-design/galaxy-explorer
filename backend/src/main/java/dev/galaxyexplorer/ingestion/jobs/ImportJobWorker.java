package dev.galaxyexplorer.ingestion.jobs;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.catalog.read.MatchType;
import dev.galaxyexplorer.catalog.read.SearchHit;
import dev.galaxyexplorer.catalog.read.SearchService;
import dev.galaxyexplorer.ingestion.images.ImageIngestionService;
import dev.galaxyexplorer.ingestion.pipeline.IngestionPipeline;
import dev.galaxyexplorer.ingestion.pipeline.IngestionReport;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRequest;
import dev.galaxyexplorer.ingestion.pipeline.ObjectOutcome;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;

/**
 * Executa um job de importacao (na thread do {@code importExecutor}). O job
 * NUNCA fica preso em RUNNING: qualquer excecao nao prevista termina FAILED.
 *
 * <p>Objeto novo gravado: o recorte hips2fits e gerado ANTES de fechar o job (ainda em
 * {@code running}, o frontend mostra "importando…"), so recorte — a ESA fica para o comando
 * {@code images}. Falha do recorte nunca muda o status do job ({@link
 * ImageIngestionService#ingestCutoutsAfterImport} nao lanca).
 */
@Component
public class ImportJobWorker {

    static final String ALREADY_IN_CATALOG = "já existia no catálogo";
    static final String NOT_FOUND = "Objeto não encontrado em SIMBAD";

    private static final Logger log = LoggerFactory.getLogger(ImportJobWorker.class);

    private final ImportJobRepository jobs;
    private final SearchService search;
    private final IngestionPipeline pipeline;
    private final ImageIngestionService images;

    public ImportJobWorker(ImportJobRepository jobs, SearchService search, IngestionPipeline pipeline,
                           ImageIngestionService images) {
        this.jobs = jobs;
        this.search = search;
        this.pipeline = pipeline;
        this.images = images;
    }

    public void execute(long jobId) {
        Long runId = null;
        try {
            ImportJob job = jobs.findById(jobId).orElseThrow(() -> new IllegalStateException("job " + jobId
                    + " sumiu"));
            jobs.markRunning(jobId);
            List<UUID> existing = search.search(job.query(), SearchService.MAX_LIMIT).stream()
                    .filter(h -> h.matchType() == MatchType.EXACT)
                    .map(SearchHit::objectId)
                    .toList();
            if (!existing.isEmpty()) {
                jobs.markFinished(jobId, ImportJobStatus.SUCCEEDED, ALREADY_IN_CATALOG, null, existing);
                return;
            }
            IngestionReport report = pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.ON_DEMAND,
                    List.of(job.query())));
            runId = report.runId();
            List<ObjectOutcome> outcomes = report.outcomes();
            List<UUID> written = outcomes.stream().filter(ObjectOutcome::ok).map(ObjectOutcome::objectId)
                    .filter(Objects::nonNull).distinct().toList();
            if (!written.isEmpty()) {
                try {
                    images.ingestCutoutsAfterImport(written);
                } catch (RuntimeException e) {
                    // defesa em profundidade: o servico ja nao lanca; imagem nunca derruba a importacao
                    log.warn("Job {}: recorte apos importacao falhou", jobId, e);
                }
                jobs.markFinished(jobId, ImportJobStatus.SUCCEEDED, null, runId, written);
            } else if (!outcomes.isEmpty() && outcomes.stream().allMatch(ObjectOutcome::notFound)) {
                jobs.markFinished(jobId, ImportJobStatus.NOT_FOUND, NOT_FOUND, runId, List.of());
            } else {
                ObjectOutcome.Kind kind = outcomes.stream().filter(o -> !o.ok() && !o.notFound())
                        .map(ObjectOutcome::kind).findFirst().orElse(ObjectOutcome.Kind.INTERNAL_ERROR);
                jobs.markFinished(jobId, ImportJobStatus.FAILED, userMessage(kind, runId), runId, List.of());
            }
        } catch (RuntimeException e) {
            log.error("Job de importacao {} falhou", jobId, e);
            jobs.markFinished(jobId, ImportJobStatus.FAILED, userMessage(ObjectOutcome.Kind.INTERNAL_ERROR, runId),
                    runId, List.of());
        }
    }

    /**
     * Mensagem para o usuario: so a CATEGORIA. O detalhe tecnico (que pode conter
     * SQL, constraint ou corpo de erro da fonte) fica no log e no
     * {@code ingestion_run.error_summary}, do lado do servidor.
     *
     * <p>Switch exaustivo sem {@code default}: um {@code Kind} novo nao compila ate
     * ganhar mensagem propria.
     *
     * @throws IllegalArgumentException para {@code WRITTEN}/{@code NOT_FOUND}, que nao sao falhas
     */
    static String userMessage(ObjectOutcome.Kind kind, Long runId) {
        String run = runId == null ? "" : " (run " + runId + ")";
        return switch (kind) {
            case SOURCE_UNAVAILABLE -> "Fonte indisponível no momento (SIMBAD/CDS); tente novamente mais tarde" + run
                    + ".";
            case SOURCE_QUERY_FAILED -> "Falha na consulta à fonte" + run + ".";
            case SCHEMA_MISMATCH -> "O esquema da fonte mudou; importação suspensa até revisão" + run + ".";
            case REJECTED -> "Dados da fonte rejeitados na validação" + run + ".";
            case WRITE_FAILED -> "Falha ao gravar no catálogo" + run + ".";
            case INTERNAL_ERROR -> "Erro interno" + run + ".";
            case WRITTEN, NOT_FOUND -> throw new IllegalArgumentException(kind + " não é uma falha");
        };
    }
}
