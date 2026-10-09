package dev.galaxyexplorer.ingestion.images;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRunRepository;
import dev.galaxyexplorer.ingestion.pipeline.RunStatus;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;

/**
 * Comando local de imagens (como o seed: profile, nao endpoint — sem superficie de ataque).
 *
 * <pre>
 *   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=images" "-Dspring-boot.run.arguments=--galaxy.images.run.sources=CUTOUT,ESA_HUBBLE,ESA_WEBB --galaxy.images.run.objects=all"
 * </pre>
 * {@code objects}: {@code all} ou termos separados por virgula (resolvidos pela busca exata local, ex.
 * {@code M51,M31}). {@code --galaxy.images.run.refresh=true} reconsulta objetos que ja tem imagem (use depois
 * de editar {@code images/curation.csv}). Imprime o relatorio, as contagens e a lista de objetos que ficaram
 * so com recorte; sai com 1 se algum run terminou {@code failed}.
 *
 * <p>Flags da carga inicial do Docker ({@code backend/docker/seed.sh}):
 * <ul>
 *   <li>{@code --galaxy.images.run.strict=true}: sai com 1 tambem se QUALQUER objeto falhou (run
 *       {@code partial}), para quem orquestra repetir — a repeticao e incremental ({@code refresh=false}).</li>
 *   <li>{@code --galaxy.images.run.skip-if-complete=true}: nao faz nada (nenhuma requisicao) se o run
 *       {@code manual} mais recente de CADA fonte pedida terminou {@code ok}. O estado vem do banco: banco
 *       novo (ex. depois de {@code down -v}) = nenhum run = carga refeita.</li>
 * </ul>
 */
@Component
@Profile("images")
public class ImageSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImageSeedRunner.class);

    private final ImageIngestionService service;
    private final ConfigurableApplicationContext context;
    private final List<String> sources;
    private final List<String> objects;
    private final boolean refresh;
    private final boolean strict;
    private final boolean skipIfComplete;
    private final IngestionRunRepository runs;

    public ImageSeedRunner(ImageIngestionService service, ConfigurableApplicationContext context,
                           IngestionRunRepository runs,
                           @Value("${galaxy.images.run.sources:CUTOUT}") List<String> sources,
                           @Value("${galaxy.images.run.objects:all}") List<String> objects,
                           @Value("${galaxy.images.run.refresh:false}") boolean refresh,
                           @Value("${galaxy.images.run.strict:false}") boolean strict,
                           @Value("${galaxy.images.run.skip-if-complete:false}") boolean skipIfComplete) {
        this.service = service;
        this.context = context;
        this.runs = runs;
        this.sources = sources;
        this.objects = objects;
        this.refresh = refresh;
        this.strict = strict;
        this.skipIfComplete = skipIfComplete;
    }

    /** Fonte de dados ({@code source.code}) onde cada tipo de ingestao de imagem grava o run. */
    static SourceCode sourceCode(ImageSource source) {
        return switch (source) {
            case CUTOUT -> SourceCode.CDS_HIPS2FITS;
            case ESA_HUBBLE -> SourceCode.ESA_HUBBLE;
            case ESA_WEBB -> SourceCode.ESA_WEBB;
        };
    }

    /** Completo = toda fonte pedida tem um run mais recente, e ele terminou {@code ok}. */
    static boolean alreadyComplete(Collection<Optional<RunStatus>> latestPerSource) {
        return !latestPerSource.isEmpty()
                && latestPerSource.stream().allMatch(s -> s.isPresent() && s.get() == RunStatus.OK);
    }

    /** Run {@code failed} sempre da 1; com {@code strict}, qualquer objeto que falhou tambem. */
    static int exitCode(boolean anyRunFailed, boolean anyOutcomeFailed, boolean strict) {
        return anyRunFailed || (strict && anyOutcomeFailed) ? 1 : 0;
    }

    static Set<ImageSource> parseSources(List<String> raw) {
        Set<ImageSource> out = EnumSet.noneOf(ImageSource.class);
        for (String s : raw) {
            if (!s.isBlank()) {
                out.add(ImageSource.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("galaxy.images.run.sources vazio");
        }
        return out;
    }

    /** {@code all} (ou vazio) = todos; senao cada termo tem de resolver exatamente para um objeto. */
    List<UUID> resolveObjects(List<String> raw) {
        List<String> terms = raw.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (terms.isEmpty() || (terms.size() == 1 && "all".equalsIgnoreCase(terms.get(0)))) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String term : terms) {
            UUID id = service.resolveExact(term);
            if (id == null) {
                missing.add(term);
            } else {
                ids.add(id);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("objetos não encontrados no catálogo local: " + missing);
        }
        return ids;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<ImageSource> requested = parseSources(sources);
        if (skipIfComplete && alreadyComplete(requested.stream()
                .map(s -> runs.latestStatus(sourceCode(s), RunTrigger.MANUAL)).toList())) {
            log.info("Imagens: o ultimo run manual de {} terminou ok; nada a fazer.", requested);
            exit(0);
            return;
        }
        ImageIngestionReport report = service.run(new ImageIngestionRequest(requested,
                resolveObjects(objects), RunTrigger.MANUAL, refresh));
        report.runIds().forEach((source, runId) -> log.info("Imagens {}: run={} status={}", source, runId,
                report.runStatuses().get(source)));
        Map<String, Long> counts = report.outcomes().stream()
                .collect(Collectors.groupingBy(o -> o.source() + " " + o.kind(), TreeMap::new, Collectors.counting()));
        counts.forEach((k, v) -> log.info("  {}: {}", k, v));
        report.outcomes().stream().filter(o -> o.kind().failure())
                .forEach(o -> log.warn("  FALHA {} {}: {}", o.source(), o.slug(), o.detail()));
        report.issues().forEach(i -> log.info("  rejeição/aviso: {}", i));
        log.info("Objetos só com recorte ({}): {}", report.cutoutOnlySlugs().size(), report.cutoutOnlySlugs());
        exit(exitCode(report.anyRunFailed(), report.outcomes().stream().anyMatch(o -> o.kind().failure()), strict));
    }

    private void exit(int code) {
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
