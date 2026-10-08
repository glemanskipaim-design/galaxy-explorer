package dev.galaxyexplorer.ingestion.jobs;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.galaxyexplorer.ingestion.pipeline.RawPayloadRepository;

/**
 * Politica de retencao das importacoes sob demanda (o endpoint e publico: sem
 * isto, POSTs com termos distintos crescem o banco sem limite).
 *
 * <p>A cada hora, apaga o que e mais antigo que {@code galaxy.imports.retention}
 * (padrao 30 dias):
 * <ul>
 *   <li>{@code import_job} terminais (succeeded/not_found/failed) — jobs ativos nunca;</li>
 *   <li>{@code raw_payload} de runs {@code on_demand} que nao gravaram nenhum objeto.
 *       Payloads de runs que gravaram objetos ficam: sao a proveniencia do catalogo.</li>
 * </ul>
 * Nao roda nos profiles {@code seed} e {@code images} (processos curtos, que nao servem importacoes).
 */
@Component
@Profile("!seed & !images")
public class ImportRetention {

    private static final Logger log = LoggerFactory.getLogger(ImportRetention.class);

    private final ImportJobRepository jobs;
    private final RawPayloadRepository rawPayloads;
    private final ImportProperties props;
    private final Clock clock;

    public ImportRetention(ImportJobRepository jobs, RawPayloadRepository rawPayloads, ImportProperties props,
                           Clock clock) {
        this.jobs = jobs;
        this.rawPayloads = rawPayloads;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT1H", fixedDelayString = "PT1H")
    public void purgeExpired() {
        Instant cutoff = clock.instant().minus(props.retention());
        int deletedJobs = jobs.deleteFinishedBefore(cutoff);
        int deletedPayloads = rawPayloads.deleteForUnproductiveOnDemandRunsBefore(cutoff);
        if (deletedJobs > 0 || deletedPayloads > 0) {
            log.info("Retencao (antes de {}): {} job(s) e {} payload(s) cru(s) apagados", cutoff, deletedJobs,
                    deletedPayloads);
        }
    }
}
