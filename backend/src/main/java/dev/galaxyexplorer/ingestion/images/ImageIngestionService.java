package dev.galaxyexplorer.ingestion.images;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import dev.galaxyexplorer.catalog.image.ImageKind;
import dev.galaxyexplorer.catalog.image.ObjectImageRepository;
import dev.galaxyexplorer.catalog.image.ObjectImageRow;
import dev.galaxyexplorer.catalog.read.MatchType;
import dev.galaxyexplorer.catalog.read.SearchHit;
import dev.galaxyexplorer.catalog.read.SearchService;
import dev.galaxyexplorer.ingestion.connector.esa.EsaProvider;
import dev.galaxyexplorer.ingestion.connector.esa.EsaSite;
import dev.galaxyexplorer.ingestion.connector.image.ImageTarget;
import dev.galaxyexplorer.ingestion.images.curation.CurationFile;
import dev.galaxyexplorer.ingestion.pipeline.RunStatus;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;

/**
 * Fachada da ingestao de imagens: o comando {@code images} e o recorte apos uma
 * importacao sob demanda passam por aqui.
 */
@Service
public class ImageIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ImageIngestionService.class);

    private final ImageTargetLoader targets;
    private final CutoutIngestion cutouts;
    private final EsaIngestion esa;
    private final ObjectImageRepository images;
    private final SearchService search;
    private final ImageProperties props;

    public ImageIngestionService(ImageTargetLoader targets, CutoutIngestion cutouts, EsaIngestion esa,
                                 ObjectImageRepository images, SearchService search, ImageProperties props) {
        this.targets = targets;
        this.cutouts = cutouts;
        this.esa = esa;
        this.images = images;
        this.search = search;
        this.props = props;
    }

    /**
     * Ordem fixa: recorte, ESA/Hubble, ESA/Webb (o hero e recalculado a cada imagem gravada, entao a ordem
     * nao muda o resultado final).
     *
     * @throws dev.galaxyexplorer.ingestion.images.curation.InvalidCurationException curadoria invalida (antes de
     *         qualquer requisicao)
     */
    public ImageIngestionReport run(ImageIngestionRequest request) {
        CurationFile curation = request.sources().contains(ImageSource.ESA_HUBBLE)
                || request.sources().contains(ImageSource.ESA_WEBB) ? loadCuration() : CurationFile.empty();
        List<ImageTarget> loaded = targets.load(request.objectIds());
        Map<ImageSource, Long> runIds = new EnumMap<>(ImageSource.class);
        Map<ImageSource, RunStatus> statuses = new EnumMap<>(ImageSource.class);
        List<ImageOutcome> outcomes = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        for (ImageSource source : ImageSource.values()) {
            if (!request.sources().contains(source)) {
                continue;
            }
            ImageRunResult result = switch (source) {
                case CUTOUT -> cutouts.run(loaded, request.trigger(), request.refresh());
                case ESA_HUBBLE -> esa.run(site(EsaProvider.HUBBLE), source, loaded, curation, request.trigger(),
                        request.refresh());
                case ESA_WEBB -> esa.run(site(EsaProvider.WEBB), source, loaded, curation, request.trigger(),
                        request.refresh());
            };
            runIds.put(source, result.runId());
            statuses.put(source, result.status());
            outcomes.addAll(result.outcomes());
            issues.addAll(result.issues());
        }
        return new ImageIngestionReport(runIds, statuses, outcomes, cutoutOnly(loaded), issues);
    }

    /**
     * Recorte dos objetos recem-importados (so hips2fits; a ESA fica para o comando {@code images}). Nunca
     * lanca: falha vira log — a importacao do objeto nao depende da imagem.
     */
    public void ingestCutoutsAfterImport(List<UUID> objectIds) {
        if (objectIds.isEmpty()) {
            return;
        }
        try {
            ImageIngestionReport report = run(new ImageIngestionRequest(Set.of(ImageSource.CUTOUT), objectIds,
                    RunTrigger.ON_DEMAND, false));
            report.outcomes().stream().filter(o -> o.kind().failure())
                    .forEach(o -> log.warn("Recorte apos importacao de {}: {} — {}", o.slug(), o.kind(), o.detail()));
        } catch (RuntimeException e) {
            log.warn("Recorte apos importacao falhou para {}", objectIds, e);
        }
    }

    /** Objetos com recorte e sem nenhuma imagem ESA (depois da execucao). */
    private List<String> cutoutOnly(List<ImageTarget> loaded) {
        List<String> out = new ArrayList<>();
        for (ImageTarget t : loaded) {
            List<ObjectImageRow> rows = images.findByObject(t.objectId());
            boolean cutout = rows.stream().anyMatch(r -> r.kind() == ImageKind.SURVEY_CUTOUT);
            boolean other = rows.stream().anyMatch(r -> r.kind() != ImageKind.SURVEY_CUTOUT);
            if (cutout && !other) {
                out.add(t.slug());
            }
        }
        return out;
    }

    private EsaSite site(EsaProvider provider) {
        return ImagesConfig.esaSites(props).stream().filter(s -> s.provider() == provider).findFirst().orElseThrow();
    }

    CurationFile loadCuration() {
        ClassPathResource resource = new ClassPathResource(props.curationFile());
        if (!resource.exists()) {
            throw new IllegalStateException("arquivo de curadoria ausente no classpath: " + props.curationFile());
        }
        try (InputStream in = resource.getInputStream()) {
            return CurationFile.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), this::resolveExact);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Termo -> objeto pela busca EXATA local; ambiguo ou ausente = {@code null}. */
    UUID resolveExact(String query) {
        List<UUID> ids;
        try {
            ids = search.search(query, SearchService.MAX_LIMIT).stream()
                    .filter(h -> h.matchType() == MatchType.EXACT).map(SearchHit::objectId).distinct().toList();
        } catch (IllegalArgumentException e) {
            return null;
        }
        return ids.size() == 1 ? ids.get(0) : null;
    }
}
