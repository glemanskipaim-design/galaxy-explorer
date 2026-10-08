package dev.galaxyexplorer.ingestion.images;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import dev.galaxyexplorer.catalog.image.HeroCandidate;
import dev.galaxyexplorer.catalog.image.HeroPolicy;
import dev.galaxyexplorer.catalog.image.ObjectImageRepository;
import dev.galaxyexplorer.catalog.object.Messier;
import dev.galaxyexplorer.catalog.source.SourceRepository;
import dev.galaxyexplorer.catalog.source.SourceRow;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import dev.galaxyexplorer.ingestion.connector.esa.AvmCandidateEvaluator;
import dev.galaxyexplorer.ingestion.connector.esa.AvmJsonParser;
import dev.galaxyexplorer.ingestion.connector.esa.AvmRecord;
import dev.galaxyexplorer.ingestion.connector.esa.EsaOutreachClient;
import dev.galaxyexplorer.ingestion.connector.esa.EsaSearchHtmlParser;
import dev.galaxyexplorer.ingestion.connector.esa.EsaSite;
import dev.galaxyexplorer.ingestion.connector.esa.SubjectNameMatcher;
import dev.galaxyexplorer.ingestion.connector.image.DownloadedImage;
import dev.galaxyexplorer.ingestion.connector.image.ImageCandidate;
import dev.galaxyexplorer.ingestion.connector.image.ImageDownloader;
import dev.galaxyexplorer.ingestion.connector.image.ImageTarget;
import dev.galaxyexplorer.ingestion.images.ImageOutcome.Kind;
import dev.galaxyexplorer.ingestion.images.curation.CurationEntry;
import dev.galaxyexplorer.ingestion.images.curation.CurationFile;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRunRepository;
import dev.galaxyexplorer.ingestion.pipeline.RawPayloadRepository;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;
import dev.galaxyexplorer.media.InvalidImageException;

/**
 * Imagens ESA/Hubble ou ESA/Webb para N objetos, num run por site. Para cada
 * objeto: busca por {@code Subject.Name} (termo {@code Messier N}, senao
 * {@code NGC N} — fato verificado: a busca indexa "Messier 51", e "M 51" nao acha
 * heic0506a) -> ate {@code max-candidates-per-search} ids -> AVM de cada (uma vez
 * por run) -> {@link AvmCandidateEvaluator} -> as melhores pela {@link HeroPolicy},
 * ate {@code max-images-per-object}, mais as da curadoria -> download do JPG
 * "screen" -> {@link ImageWriter}. Toda rejeicao vai para o error_summary.
 */
@Component
public class EsaIngestion {

    public static final String CONNECTOR_VERSION = "esa-avm/1";
    public static final String DATASET_SCREEN = "esa_screen_jpg";

    private static final Logger log = LoggerFactory.getLogger(EsaIngestion.class);
    private static final Pattern NGC = Pattern.compile("^NGC\\s+(\\d{1,4})$");
    /** Abaixo disto, "0 resultados para todos" nao basta para suspeitar do HTML. */
    private static final int MIN_SEARCHES_FOR_HTML_WARNING = 3;

    /** AVM de um id ja buscada neste run: registro ou motivo de nao servir. */
    private record LoadedAvm(AvmRecord record, Long payloadId, String error) {
    }

    private static final class Counters {
        int searches;
        int emptySearches;
    }

    private final EsaOutreachClient client;
    private final ImageDownloader downloader;
    private final ImageWriter writer;
    private final ObjectImageRepository images;
    private final RawPayloadRepository rawPayloads;
    private final IngestionRunRepository runs;
    private final SourceRepository sources;
    private final ImageProperties props;

    public EsaIngestion(EsaOutreachClient client, @Qualifier("esaImageDownloader") ImageDownloader downloader,
                        ImageWriter writer, ObjectImageRepository images, RawPayloadRepository rawPayloads,
                        IngestionRunRepository runs, SourceRepository sources, ImageProperties props) {
        this.client = client;
        this.downloader = downloader;
        this.writer = writer;
        this.images = images;
        this.rawPayloads = rawPayloads;
        this.runs = runs;
        this.sources = sources;
        this.props = props;
    }

    public ImageRunResult run(EsaSite site, ImageSource imageSource, List<ImageTarget> targets, CurationFile curation,
                              RunTrigger trigger, boolean refresh) {
        SourceRow source = sources.getByCode(site.provider().source());
        long runId = runs.open(source.id(), trigger, CONNECTOR_VERSION);
        Map<String, LoadedAvm> avms = new HashMap<>();
        List<ImageOutcome> outcomes = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Counters counters = new Counters();
        try {
            for (ImageTarget t : targets) {
                outcomes.add(runOne(site, imageSource, t, source, runId, curation, refresh, avms, notes, counters));
            }
        } catch (RuntimeException e) {
            log.error("Run {} da {} abortado por erro inesperado", runId, site.provider().label(), e);
            outcomes.add(new ImageOutcome(null, null, imageSource, Kind.WRITE_FAILED, "erro inesperado: " + e));
        }
        if (counters.searches >= MIN_SEARCHES_FOR_HTML_WARNING && counters.emptySearches == counters.searches) {
            notes.add(0, "a busca da " + site.provider().label() + " devolveu 0 imagens para todos os "
                    + counters.searches + " alvos — provável mudança no HTML do site");
        }
        return RunResults.close(runs, runId, imageSource, outcomes, notes);
    }

    private ImageOutcome runOne(EsaSite site, ImageSource imageSource, ImageTarget t, SourceRow source, long runId,
                                CurationFile curation, boolean refresh, Map<String, LoadedAvm> avms,
                                List<String> notes, Counters counters) {
        List<CurationEntry> curated = curation.forObject(t.objectId(), site.provider());
        Set<String> excluded = curated.stream().filter(e -> e.action() == CurationEntry.Action.EXCLUDE)
                .map(CurationEntry::imageId).collect(Collectors.toSet());
        Map<String, CurationEntry> chosen = new LinkedHashMap<>();
        curated.stream().filter(e -> e.action() != CurationEntry.Action.EXCLUDE)
                .forEach(e -> chosen.putIfAbsent(e.imageId(), e));
        if (!refresh && !images.findSourceImageIds(t.objectId(), source.id()).isEmpty()) {
            return outcome(imageSource, t, Kind.UNCHANGED, "já tem imagem da " + site.provider().label());
        }
        String term = searchTerm(t.identifiers());
        if (term == null && chosen.isEmpty()) {
            return outcome(imageSource, t, Kind.NO_VALID_CANDIDATE, "sem designação Messier/NGC para buscar");
        }
        List<String> rejections = new ArrayList<>();
        try {
            LinkedHashSet<String> ids = new LinkedHashSet<>(chosen.keySet());
            if (term != null) {
                RawPayload html = client.search(site, term);
                rawPayloads.insert(runId, html);
                List<String> found = EsaSearchHtmlParser.imageIds(html.body());
                counters.searches++;
                if (found.isEmpty()) {
                    counters.emptySearches++;
                }
                ids.addAll(found.subList(0, Math.min(found.size(), props.esa().maxCandidatesPerSearch())));
            }
            List<ImageCandidate> manual = new ArrayList<>();
            List<ImageCandidate> automatic = new ArrayList<>();
            Map<String, Long> metadataPayloads = new HashMap<>();
            for (String id : ids) {
                CurationEntry entry = chosen.get(id);
                if (entry == null && excluded.contains(id)) {
                    rejections.add(id + ": excluída pela curadoria");
                    continue;
                }
                LoadedAvm avm = avms.get(id);
                if (avm == null) {
                    avm = loadAvm(site, id, runId);
                    avms.put(id, avm);
                }
                if (avm.error() != null) {
                    rejections.add(id + ": " + avm.error());
                    continue;
                }
                AvmCandidateEvaluator.Evaluation ev = AvmCandidateEvaluator.evaluate(site, avm.record(), t,
                        entry != null, entry != null && entry.action() == CurationEntry.Action.HERO);
                if (ev instanceof AvmCandidateEvaluator.Accepted accepted) {
                    (entry != null ? manual : automatic).add(accepted.candidate());
                    metadataPayloads.put(id, avm.payloadId());
                } else if (ev instanceof AvmCandidateEvaluator.Rejected rejected) {
                    rejections.add(id + ": " + rejected.reason());
                }
            }
            List<ImageCandidate> selected = new ArrayList<>(manual);
            selected.addAll(best(automatic, props.esa().maxImagesPerObject()));
            List<String> stored = new ArrayList<>();
            for (ImageCandidate c : selected) {
                try {
                    DownloadedImage d = downloader.download(c.downloadUrl(), site.provider().label());
                    writer.write(t, c, d, runId, metadataPayloads.get(c.sourceImageId()), DATASET_SCREEN);
                    stored.add(c.sourceImageId());
                } catch (SourceQueryException | InvalidImageException e) {
                    rejections.add(c.sourceImageId() + ": download recusado: " + e.getMessage());
                }
            }
            writer.finishSource(t.objectId(), source.id(), idsOf(selected, false), "automatic");
            writer.finishSource(t.objectId(), source.id(), idsOf(selected, true), "manual");
            rejections.forEach(r -> notes.add(t.slug() + " / " + r));
            if (!stored.isEmpty()) {
                return outcome(imageSource, t, Kind.STORED, String.join(", ", stored));
            }
            String why = term != null && ids.isEmpty() ? "busca por '" + term + "' sem resultados"
                    : "nenhuma candidata aceita (" + rejections.size() + " rejeitada(s))";
            return outcome(imageSource, t, Kind.NO_VALID_CANDIDATE, why);
        } catch (SourceUnavailableException e) {
            rejections.forEach(r -> notes.add(t.slug() + " / " + r));
            log.warn("{}: {} indisponível — {}", t.slug(), site.provider().label(), e.getMessage());
            return outcome(imageSource, t, Kind.SOURCE_UNAVAILABLE, site.provider().label() + " indisponível: "
                    + e.getMessage());
        } catch (SourceQueryException e) {
            rejections.forEach(r -> notes.add(t.slug() + " / " + r));
            log.warn("{}: consulta à {} falhou — {}", t.slug(), site.provider().label(), e.getMessage());
            return outcome(imageSource, t, Kind.SOURCE_QUERY_FAILED, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("{}: falha ao gravar imagem da {}", t.slug(), site.provider().label(), e);
            return outcome(imageSource, t, Kind.WRITE_FAILED, "falha ao gravar: " + e.getMessage());
        }
    }

    private LoadedAvm loadAvm(EsaSite site, String id, long runId) {
        RawPayload payload;
        try {
            payload = client.avm(site, id);
        } catch (SourceQueryException e) {
            return new LoadedAvm(null, null, "AVM recusada: " + e.getMessage());
        }
        long payloadId;
        try {
            payloadId = rawPayloads.insert(runId, payload);
        } catch (DataAccessException e) {
            return new LoadedAvm(null, null, "AVM não gravável em raw_payload: " + e.getMostSpecificCause().getMessage());
        }
        if (payload.format() != RawPayload.BodyFormat.JSON) {
            return new LoadedAvm(null, payloadId, "AVM não é um objeto JSON");
        }
        try {
            return new LoadedAvm(AvmJsonParser.parse(id, payload.body()), payloadId, null);
        } catch (IllegalArgumentException e) {
            return new LoadedAvm(null, payloadId, "AVM em forma não suportada: " + e.getMessage());
        }
    }

    /** As {@code max} melhores candidatas automaticas, pela mesma ordem da {@link HeroPolicy}. */
    static List<ImageCandidate> best(List<ImageCandidate> automatic, int max) {
        List<HeroCandidate> ranked = new ArrayList<>();
        for (int i = 0; i < automatic.size(); i++) {
            ImageCandidate c = automatic.get(i);
            ranked.add(new HeroCandidate(i, c.kind(), c.source().name(), c.curatedHero(), c.coverageRatio(),
                    c.releaseDate(), c.separationArcmin()));
        }
        return HeroPolicy.order(ranked).stream().limit(max).map(h -> automatic.get((int) h.id())).toList();
    }

    /**
     * Termo de busca: designacao Messier por extenso ({@code M  51} -> {@code Messier 51}); senao
     * {@code NGC N}; senao {@code null}.
     */
    static String searchTerm(List<String> identifiers) {
        for (String id : identifiers) {
            Matcher m = Messier.DESIGNATION.matcher(id.trim());
            if (m.matches()) {
                return SubjectNameMatcher.longMessier(Integer.parseInt(m.group(1)));
            }
        }
        for (String id : identifiers) {
            Matcher m = NGC.matcher(id.trim());
            if (m.matches()) {
                return "NGC " + Integer.parseInt(m.group(1));
            }
        }
        return null;
    }

    private static Set<String> idsOf(List<ImageCandidate> selected, boolean curated) {
        return selected.stream().filter(c -> c.curated() == curated).map(ImageCandidate::sourceImageId)
                .collect(Collectors.toSet());
    }

    private static ImageOutcome outcome(ImageSource source, ImageTarget t, Kind kind, String detail) {
        return new ImageOutcome(t.objectId(), t.slug(), source, kind, detail);
    }
}
