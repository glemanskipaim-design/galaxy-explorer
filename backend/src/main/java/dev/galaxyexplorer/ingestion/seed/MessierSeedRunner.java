package dev.galaxyexplorer.ingestion.seed;

import java.util.List;
import java.util.function.Predicate;

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
import dev.galaxyexplorer.catalog.identifier.IdentifierNormalizer;
import dev.galaxyexplorer.catalog.identifier.IdentifierRepository;
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
 *
 * <p>{@code --galaxy.seed.only-missing=true} (usado pela carga inicial do Docker): so consulta
 * os nomes que ainda nao existem no catalogo local (identificador exato). Torna o seed
 * idempotente e retomavel — se nada falta, nao faz nenhuma requisicao externa; se a rede
 * caiu no meio, a proxima execucao pede so o que faltou. Nesse modo qualquer consulta que
 * falhe da codigo 1 (mesmo com status PARTIAL), para quem orquestra saber que deve repetir.
 */
@Component
@Profile("seed")
public class MessierSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MessierSeedRunner.class);

    private final IngestionPipeline pipeline;
    private final JdbcClient jdbc;
    private final ConfigurableApplicationContext context;
    private final IdentifierRepository identifiers;
    private final List<String> configuredQueries;
    private final boolean onlyMissing;

    /**
     * @param configuredQueries {@code galaxy.seed.queries}; vazio = os 110 Messier
     * @param onlyMissing       {@code galaxy.seed.only-missing}: pula nomes ja presentes no catalogo local
     */
    public MessierSeedRunner(IngestionPipeline pipeline, JdbcClient jdbc, ConfigurableApplicationContext context,
                             IdentifierRepository identifiers,
                             @Value("${galaxy.seed.queries:}") List<String> configuredQueries,
                             @Value("${galaxy.seed.only-missing:false}") boolean onlyMissing) {
        this.pipeline = pipeline;
        this.jdbc = jdbc;
        this.context = context;
        this.identifiers = identifiers;
        this.configuredQueries = configuredQueries;
        this.onlyMissing = onlyMissing;
    }

    static List<String> queriesToRun(List<String> configured) {
        List<String> explicit = configured == null ? List.of()
                : configured.stream().map(String::trim).filter(q -> !q.isEmpty()).toList();
        return explicit.isEmpty() ? MessierDesignations.all() : explicit;
    }

    /** Mantem so as consultas que ainda nao existem localmente (ordem preservada). */
    static List<String> missingOnly(List<String> queries, Predicate<String> existsLocally) {
        return queries.stream().filter(existsLocally.negate()).toList();
    }

    /** FAILED sempre da 1; no modo {@code only-missing}, qualquer consulta que falhou tambem. */
    static int exitCode(RunStatus status, int recordsFailed, boolean onlyMissing) {
        if (status == RunStatus.FAILED) {
            return 1;
        }
        return onlyMissing && recordsFailed > 0 ? 1 : 0;
    }

    private boolean existsLocally(String query) {
        return !identifiers.findExact(IdentifierNormalizer.normalize(query), 1).isEmpty();
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> queries = queriesToRun(configuredQueries);
        if (onlyMissing) {
            queries = missingOnly(queries, this::existsLocally);
            if (queries.isEmpty()) {
                log.info("Seed: todos os nomes ja estao no catalogo local; nenhuma requisicao externa.");
                System.exit(SpringApplication.exit(context, () -> 0));
                return;
            }
            log.info("Seed: {} nome(s) ainda ausente(s) no catalogo local: {}", queries.size(), queries);
        }
        IngestionReport report = pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.SEED, queries));
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
        int code = exitCode(report.status(), report.recordsFailed(), onlyMissing);
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
