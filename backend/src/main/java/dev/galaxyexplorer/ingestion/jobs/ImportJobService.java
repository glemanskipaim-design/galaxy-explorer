package dev.galaxyexplorer.ingestion.jobs;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import dev.galaxyexplorer.catalog.identifier.IdentifierNormalizer;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRunRepository;

/**
 * Importacao sob demanda: recebe um termo, cria (ou reaproveita) um job e o
 * entrega a fila de uma thread so.
 */
@Service
public class ImportJobService {

    static final String INTERRUPTED = "interrompido por reinício do servidor";
    static final String QUEUE_FULL = "fila de importação cheia; tente novamente em instantes";

    private static final Logger log = LoggerFactory.getLogger(ImportJobService.class);

    private final ImportJobRepository jobs;
    private final ImportJobWorker worker;
    private final ThreadPoolTaskExecutor executor;
    private final IngestionRunRepository runs;
    private final Environment environment;

    public ImportJobService(ImportJobRepository jobs, ImportJobWorker worker,
                            @Qualifier("importExecutor") ThreadPoolTaskExecutor executor, IngestionRunRepository runs,
                            Environment environment) {
        this.jobs = jobs;
        this.worker = worker;
        this.executor = executor;
        this.runs = runs;
        this.environment = environment;
    }

    /**
     * Idempotente por termo: se ja existe job ativo para o mesmo termo
     * normalizado, devolve ele. A corrida entre duas requisicoes simultaneas e
     * resolvida pelo indice unico parcial {@code one_active_import}.
     *
     * <p>Fila cheia e checada ANTES de inserir: um POST rejeitado com 429 nao
     * grava nada (um loop de termos distintos nao cresce {@code import_job}).
     *
     * @throws ImportQueueFullException fila cheia (nenhum job gravado)
     */
    public ImportJob submit(String rawQuery) {
        String query = rawQuery.trim();
        String normalized = IdentifierNormalizer.normalize(query);
        Optional<ImportJob> active = jobs.findActiveByNormalized(normalized);
        if (active.isPresent()) {
            return active.get();
        }
        if (queueFull()) {
            throw new ImportQueueFullException(QUEUE_FULL);
        }
        ImportJob job;
        try {
            job = jobs.insertQueued(query, normalized);
        } catch (DuplicateKeyException race) {
            return jobs.findActiveByNormalized(normalized).orElseThrow(() -> race);
        }
        long id = job.id();
        try {
            executor.execute(() -> worker.execute(id));
        } catch (TaskRejectedException e) {
            // corrida rara entre a checagem e o execute: o job rejeitado nao fica no banco
            jobs.deleteById(id);
            throw new ImportQueueFullException(QUEUE_FULL);
        }
        return job;
    }

    private boolean queueFull() {
        return executor.getQueueSize() >= executor.getQueueCapacity();
    }

    public Optional<ImportJob> find(long id) {
        return jobs.findById(id);
    }

    /**
     * Na subida do servidor: jobs e runs deixados ativos por um restart viram
     * FAILED. Nao roda no profile {@code seed}, que e outro processo e poderia
     * derrubar jobs de um servidor rodando ao mesmo tempo.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        if (environment.acceptsProfiles(Profiles.of("seed"))) {
            return;
        }
        int failedJobs = jobs.failInterrupted(INTERRUPTED);
        int failedRuns = runs.failDangling(INTERRUPTED);
        if (failedJobs > 0 || failedRuns > 0) {
            log.warn("Recuperacao apos reinicio: {} job(s) e {} run(s) marcados como failed", failedJobs, failedRuns);
        }
    }
}
