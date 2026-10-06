package dev.galaxyexplorer.ingestion.seed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.ingestion.pipeline.IngestionPipeline;
import dev.galaxyexplorer.ingestion.pipeline.IngestionReport;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRequest;
import dev.galaxyexplorer.ingestion.pipeline.ObjectOutcome;
import dev.galaxyexplorer.ingestion.pipeline.RunStatus;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;

/**
 * Carga inicial: os 110 objetos Messier, via ingestao real do SIMBAD.
 *
 * <pre>
 *   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=seed"
 * </pre>
 *
 * Reingestao de nomes especificos (ex. depois de uma migracao de mapeamento):
 * <pre>
 *   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=seed" "-Dspring-boot.run.arguments=--galaxy.seed.queries=M51,Vega"
 * </pre>
 *
 * Profile em vez de endpoint admin: endpoint exigiria autenticacao (que ainda
 * nao existe); um comando local nao abre superficie de ataque. Imprime o
 * relatorio e encerra o processo — codigo 0 para OK/PARTIAL, 1 para FAILED.
 */
@Component
@Profile("seed")
public class MessierSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MessierSeedRunner.class);

    private final IngestionPipeline pipeline;
    private final JdbcClient jdbc;
    private final ConfigurableApplicationContext context;
    private final List<String> configuredQueries;

    /** @param configuredQueries {@code galaxy.seed.queries}; vazio = os 110 Messier */
    public MessierSeedRunner(IngestionPipeline pipeline, JdbcClient jdbc, ConfigurableApplicationContext context,
                             @Value("${galaxy.seed.queries:}") List<String> configuredQueries) {
        this.pipeline = pipeline;
        this.jdbc = jdbc;
        this.context = context;
        this.configuredQueries = configuredQueries;
    }

    static List<String> queriesToRun(List<String> configured) {
        List<String> explicit = configured == null ? List.of()
                : configured.stream().map(String::trim).filter(q -> !q.isEmpty()).toList();
        return explicit.isEmpty() ? MessierDesignations.all() : explicit;
    }

    @Override
    public void run(ApplicationArguments args) {
        IngestionReport report = pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.SEED,
                queriesToRun(configuredQueries)));
        log.info("Seed: run={} status={} ok={} falhas={}", report.runId(), report.status(),
                report.recordsOk(), report.recordsFailed());
        for (ObjectOutcome o : report.outcomes()) {
            if (!o.ok()) {
                log.warn("  FALHA {}: {}", o.query(), o.failureReason());
            }
        }
        for (String issue : report.issues()) {
            log.info("  aviso: {}", issue);
        }
        List<String> others = jdbc.sql("""
                SELECT o.display_name || ' (otype=' || t.source_type || ')'
                FROM celestial_object o
                JOIN object_category c ON c.id = o.category_id
                JOIN object_source_type t ON t.object_id = o.id
                WHERE c.code = 'other'
                ORDER BY o.display_name
                """)
                .query(String.class)
                .list();
        log.info("Objetos classificados como 'other' (otype sem regra de mapeamento): {}", others);
        int code = report.status() == RunStatus.FAILED ? 1 : 0;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
