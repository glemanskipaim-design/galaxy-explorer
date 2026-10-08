package dev.galaxyexplorer.ingestion.images;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public ImageSeedRunner(ImageIngestionService service, ConfigurableApplicationContext context,
                           @Value("${galaxy.images.run.sources:CUTOUT}") List<String> sources,
                           @Value("${galaxy.images.run.objects:all}") List<String> objects,
                           @Value("${galaxy.images.run.refresh:false}") boolean refresh) {
        this.service = service;
        this.context = context;
        this.sources = sources;
        this.objects = objects;
        this.refresh = refresh;
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
        ImageIngestionReport report = service.run(new ImageIngestionRequest(parseSources(sources),
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
        int code = report.anyRunFailed() ? 1 : 0;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
