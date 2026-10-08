package dev.galaxyexplorer.ingestion.images;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.catalog.image.HipsSurveySnapshotRepository;
import dev.galaxyexplorer.catalog.image.ImageKind;
import dev.galaxyexplorer.catalog.image.ObjectImageRepository;
import dev.galaxyexplorer.catalog.source.SourceRepository;
import dev.galaxyexplorer.catalog.source.SourceRow;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import dev.galaxyexplorer.ingestion.connector.hips2fits.CutoutPlan;
import dev.galaxyexplorer.ingestion.connector.hips2fits.CutoutPlanner;
import dev.galaxyexplorer.ingestion.connector.hips2fits.Hips2FitsClient;
import dev.galaxyexplorer.ingestion.connector.hips2fits.HipsPropertiesParser;
import dev.galaxyexplorer.ingestion.connector.hips2fits.HipsSurvey;
import dev.galaxyexplorer.ingestion.connector.hips2fits.HipsSurveyMetadata;
import dev.galaxyexplorer.ingestion.connector.image.DownloadedImage;
import dev.galaxyexplorer.ingestion.connector.image.ImageCandidate;
import dev.galaxyexplorer.ingestion.connector.image.ImageTarget;
import dev.galaxyexplorer.ingestion.images.ImageOutcome.Kind;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRunRepository;
import dev.galaxyexplorer.ingestion.pipeline.RawPayloadRepository;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;
import dev.galaxyexplorer.media.InvalidImageException;

/**
 * Recortes hips2fits para N objetos, num run {@code CDS_HIPS2FITS}. O
 * {@code properties} de cada survey usado e buscado UMA vez por run (payload +
 * {@code hips_survey_snapshot}); credito e licenca do recorte vem dele.
 */
@Component
public class CutoutIngestion {

    public static final String CONNECTOR_VERSION = "hips2fits/1";

    private static final Logger log = LoggerFactory.getLogger(CutoutIngestion.class);

    private record SurveyContext(HipsSurveyMetadata metadata, long payloadId) {
    }

    private final Hips2FitsClient client;
    private final CutoutPlanner planner;
    private final ImageWriter writer;
    private final ObjectImageRepository images;
    private final HipsSurveySnapshotRepository snapshots;
    private final RawPayloadRepository rawPayloads;
    private final IngestionRunRepository runs;
    private final SourceRepository sources;

    public CutoutIngestion(Hips2FitsClient client, CutoutPlanner planner, ImageWriter writer,
                           ObjectImageRepository images, HipsSurveySnapshotRepository snapshots,
                           RawPayloadRepository rawPayloads, IngestionRunRepository runs, SourceRepository sources) {
        this.client = client;
        this.planner = planner;
        this.writer = writer;
        this.images = images;
        this.snapshots = snapshots;
        this.rawPayloads = rawPayloads;
        this.runs = runs;
        this.sources = sources;
    }

    public ImageRunResult run(List<ImageTarget> targets, RunTrigger trigger, boolean refresh) {
        SourceRow source = sources.getByCode(SourceCode.CDS_HIPS2FITS);
        long runId = runs.open(source.id(), trigger, CONNECTOR_VERSION);
        Map<String, SurveyContext> surveys = new HashMap<>();
        List<ImageOutcome> outcomes = new ArrayList<>();
        try {
            for (ImageTarget t : targets) {
                outcomes.add(runOne(t, source, runId, refresh, surveys));
            }
        } catch (RuntimeException e) {
            log.error("Run {} de recortes abortado por erro inesperado", runId, e);
            outcomes.add(new ImageOutcome(null, null, ImageSource.CUTOUT, Kind.WRITE_FAILED,
                    "erro inesperado: " + e));
        }
        return RunResults.close(runs, runId, ImageSource.CUTOUT, outcomes, List.of());
    }

    private ImageOutcome runOne(ImageTarget t, SourceRow source, long runId, boolean refresh,
                                Map<String, SurveyContext> surveys) {
        if (!t.hasPosition()) {
            return outcome(t, Kind.NO_POSITION, "objeto sem posição catalogada");
        }
        Optional<CutoutPlan> planned = planner.plan(t);
        if (planned.isEmpty()) {
            return outcome(t, Kind.NO_VALID_CANDIDATE, "nenhum survey configurado cobre o recorte");
        }
        CutoutPlan plan = planned.get();
        Set<String> existing = images.findSourceImageIds(t.objectId(), source.id());
        if (!refresh && existing.contains(plan.survey().hipsId())) {
            return outcome(t, Kind.UNCHANGED, "já tem recorte de " + plan.survey().hipsId());
        }
        try {
            SurveyContext ctx = surveys.get(plan.survey().code());
            if (ctx == null) {
                ctx = loadSurvey(plan.survey(), runId);
                surveys.put(plan.survey().code(), ctx);
            }
            DownloadedImage d = client.cutout(plan);
            writer.write(t, candidate(t, plan, ctx.metadata(), d), d, runId, ctx.payloadId(),
                    Hips2FitsClient.DATASET_CUTOUT);
            writer.finishSource(t.objectId(), source.id(), Set.of(plan.survey().hipsId()), "automatic");
            return outcome(t, Kind.STORED, plan.survey().hipsId() + ", fov " + plan.fovDeg() + "°");
        } catch (SourceUnavailableException e) {
            log.warn("Recorte de {}: hips2fits indisponível — {}", t.slug(), e.getMessage());
            return outcome(t, Kind.SOURCE_UNAVAILABLE, "hips2fits indisponível: " + e.getMessage());
        } catch (SourceQueryException | InvalidImageException e) {
            log.warn("Recorte de {} recusado: {}", t.slug(), e.getMessage());
            return outcome(t, Kind.SOURCE_QUERY_FAILED, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Recorte de {}: falha ao gravar", t.slug(), e);
            return outcome(t, Kind.WRITE_FAILED, "falha ao gravar: " + e.getMessage());
        }
    }

    private SurveyContext loadSurvey(HipsSurvey survey, long runId) {
        RawPayload payload = client.properties(survey);
        long payloadId = rawPayloads.insert(runId, payload);
        HipsSurveyMetadata metadata = HipsPropertiesParser.toMetadata(HipsPropertiesParser.parse(payload.body()),
                survey);
        snapshots.insert(survey.hipsId(), payload.requestUrl(), metadata.obsTitle(), metadata.obsCopyright(),
                metadata.obsCopyrightUrl(), metadata.hipsLicense(), metadata.hipsCopyright(),
                metadata.bibReference(), payloadId, payload.retrievedAt());
        return new SurveyContext(metadata, payloadId);
    }

    /** Credito, licenca e titulo: literais do {@code properties}. A "pagina" do recorte e a propria URL. */
    static ImageCandidate candidate(ImageTarget t, CutoutPlan plan, HipsSurveyMetadata m, DownloadedImage d) {
        return new ImageCandidate(SourceCode.CDS_HIPS2FITS, plan.survey().hipsId(), ImageKind.SURVEY_CUTOUT,
                m.obsTitle(), "CDS hips2fits (" + m.obsTitle() + ")", plan.survey().shortLabel(), m.obsCopyright(),
                rightsText(m), m.hipsLicense(), m.obsCopyrightUrl(), m.obsTitle(), null, null, d.requestUrl(), null,
                plan.raDeg(), plan.decDeg(), plan.fovDeg(), plan.fovBasis(), null, null,
                plan.derivedFromMeasurementId(),
                "recorte centrado na posição preferida do objeto"
                        + (t.positionSource() == null ? "" : " (" + t.positionSource() + ")"),
                URI.create(d.requestUrl()), false, false);
    }

    /**
     * Declaracao de direitos do recorte: licenciante ({@code hips_copyright}) e licenca ({@code hips_license}),
     * literais do {@code properties} — ex. {@code HiPS © CNRS/Unistra — ODbL-1.0}. Sem {@code hips_copyright}, so a
     * licenca, dita como tal (nunca um licenciante presumido).
     */
    static String rightsText(HipsSurveyMetadata m) {
        return m.hipsCopyright() == null ? "HiPS sob " + m.hipsLicense() + " (properties sem hips_copyright)"
                : "HiPS © " + m.hipsCopyright() + " — " + m.hipsLicense();
    }

    private static ImageOutcome outcome(ImageTarget t, Kind kind, String detail) {
        return new ImageOutcome(t.objectId(), t.slug(), ImageSource.CUTOUT, kind, detail);
    }

}
