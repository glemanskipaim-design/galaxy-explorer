package dev.galaxyexplorer.ingestion.images;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.catalog.image.ImageKind;
import dev.galaxyexplorer.catalog.image.ObjectImageRepository;
import dev.galaxyexplorer.catalog.image.ObjectImageRow;
import dev.galaxyexplorer.ingestion.images.curation.CurationFile;
import dev.galaxyexplorer.ingestion.images.curation.InvalidCurationException;
import dev.galaxyexplorer.ingestion.pipeline.IngestionPipeline;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRequest;
import dev.galaxyexplorer.ingestion.pipeline.RunStatus;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;
import dev.galaxyexplorer.media.ImageFileStore;
import dev.galaxyexplorer.support.FixtureHips2FitsClient;
import dev.galaxyexplorer.support.Fixtures;
import dev.galaxyexplorer.support.Samples;

/**
 * M51/M81/M94 (e, nos testes de M31/curadoria, o conjunto de amostra) ingeridos das fixtures reais do SIMBAD;
 * imagens das fixtures reais do CDS e da ESA.
 */
class ImageIngestionIntegrationTest extends IntegrationTestBase {

    @Autowired
    IngestionPipeline pipeline;
    @Autowired
    ImageIngestionService service;
    @Autowired
    CutoutIngestion cutouts;
    @Autowired
    EsaIngestion esa;
    @Autowired
    ImageTargetLoader targets;
    @Autowired
    ObjectImageRepository images;
    @Autowired
    ImageFileStore files;
    @Autowired
    ImageProperties props;

    UUID m51;

    @BeforeEach
    void seed() {
        pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.SEED, List.of("M51", "M81", "M94")));
        m51 = jdbc.sql("SELECT id FROM celestial_object WHERE slug = 'messier-51'").query(UUID.class).single();
    }

    private ImageIngestionReport runM51(boolean refresh) {
        return service.run(new ImageIngestionRequest(Set.of(ImageSource.CUTOUT, ImageSource.ESA_HUBBLE), List.of(m51),
                RunTrigger.MANUAL, refresh));
    }

    @Test
    void m51GetsCutoutAndHeic0506aAsHeroWithFilesAndProvenance() {
        ImageIngestionReport report = runM51(false);

        List<ObjectImageRow> rows = images.findByObject(m51);
        assertThat(rows).extracting(ObjectImageRow::sourceImageId)
                .containsExactly("heic0506a", "CDS/P/PanSTARRS/DR1/color-z-zg-g");
        ObjectImageRow hero = rows.get(0);
        assertThat(hero.hero()).isTrue();
        assertThat(hero.creditVerbatim())
                .isEqualTo("NASA, ESA, S. Beckwith (STScI), and The Hubble Heritage Team (STScI/AURA)");
        assertThat(hero.licenseName()).isEqualTo("Creative Commons Attribution 4.0 International");
        assertThat(hero.widthPx()).isEqualTo(1280);
        assertThat(hero.heightPx()).isEqualTo(888); // lido do JPEG real (a AVM diz 889)
        assertThat(hero.imageUrl()).isEqualTo("https://cdn.esahubble.org/archives/images/screen/heic0506a.jpg");
        assertThat(hero.contentSha256())
                .isEqualTo(ImageFileStore.sha256Hex(Fixtures.readBytes("esa/hubble_screen_heic0506a.jpg")));
        // menor lado do quadro da AVM (7965 px x 1,3885e-5 deg/px = 6,636') / ang_size_major (9,0438')
        assertThat(hero.coverageRatio()).isCloseTo(0.734, within(0.001));
        assertThat(coverageInDb(m51, "heic0506a")).isCloseTo(0.734, within(0.001));

        ObjectImageRow cutout = rows.get(1);
        assertThat(cutout.hero()).isFalse();
        assertThat(cutout.creditVerbatim()).isEqualTo("PS1 Science Consortium");
        assertThat(cutout.licenseCode()).isEqualTo("ODbL-1.0");
        // licenciante (hips_copyright) + licenca (hips_license), nao o codigo repetido
        assertThat(cutout.rightsText()).isEqualTo("HiPS © CNRS/Unistra — ODbL-1.0");
        assertThat(cutout.providerShort()).isEqualTo("PanSTARRS via CDS");
        assertThat(cutout.sourceAcknowledgment())
                .isEqualTo("This research made use of hips2fits, a service provided by CDS.");
        assertThat(cutout.fovBasis()).contains("ang_size_major = 9,0438′");

        for (ObjectImageRow r : rows) {
            assertThat(files.resolve(r.contentSha256().substring(0, 2) + "/" + r.contentSha256() + ".jpg")).isPresent();
            assertThat(images.findStorageKeyBySha(r.thumbSha256())).isPresent();
        }
        assertThat(jdbc.sql("SELECT dataset FROM raw_payload ORDER BY dataset").query(String.class).list())
                .contains("hips_properties", "hips2fits_cutout", "esa_search_html", "esa_avm_json", "esa_screen_jpg");
        assertThat(count("hips_survey_snapshot")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT hips_copyright FROM hips_survey_snapshot").query(String.class).single())
                .isEqualTo("CNRS/Unistra");
        // payload do download guarda o sha dos BYTES e a referencia ao arquivo
        assertThat(jdbc.sql("SELECT content_sha256 FROM raw_payload WHERE dataset = 'esa_screen_jpg'")
                .query(String.class).single()).isEqualTo(hero.contentSha256());
        assertThat(report.runStatuses()).containsEntry(ImageSource.CUTOUT, RunStatus.OK);
        assertThat(report.cutoutOnlySlugs()).isEmpty();
        // rejeicoes reais da busca (ex. heic0506c, imagem de contexto DSS2) vao para o error_summary
        String summary = jdbc.sql("SELECT error_summary FROM ingestion_run WHERE id = :id")
                .param("id", report.runIds().get(ImageSource.ESA_HUBBLE)).query(String.class).single();
        assertThat(summary).contains("messier-51 / heic0506c: Facility");
    }

    @Test
    void rerunWithoutRefreshMakesNoRequestsAndNoNewRows() {
        runM51(false);
        int rows = count("object_image");
        fixtureCutouts().reset();
        fixtureEsaClient().reset();

        ImageIngestionReport again = runM51(false);

        assertThat(count("object_image")).isEqualTo(rows);
        assertThat(fixtureCutouts().cutoutCalls()).isZero();
        assertThat(fixtureEsaClient().searchCalls()).isZero();
        assertThat(again.outcomes()).allMatch(o -> o.kind() == ImageOutcome.Kind.UNCHANGED);
    }

    @Test
    void curationExcludeRemovesTheImageOnRefresh() {
        runM51(false);
        CurationFile exclude = CurationFile.parse("object_query,provider,image_id,action,note\nM51,HUBBLE,heic0506a,exclude,teste\n",
                service::resolveExact);

        esa.run(ImagesConfig.esaSites(props).get(0), ImageSource.ESA_HUBBLE, targets.load(List.of(m51)), exclude,
                RunTrigger.MANUAL, true);

        List<ObjectImageRow> rows = images.findByObject(m51);
        assertThat(rows).extracting(ObjectImageRow::sourceImageId).doesNotContain("heic0506a");
        assertThat(rows).filteredOn(ObjectImageRow::hero).hasSize(1);
    }

    @Test
    void invalidCurationAbortsWithEveryProblem() {
        assertThatThrownBy(() -> CurationFile.parse(
                "object_query,provider,image_id,action,note\nXyzzy,HST,../x,keep,\n", service::resolveExact))
                .isInstanceOf(InvalidCurationException.class)
                .hasMessageContaining("provider 'HST'").hasMessageContaining("image_id '../x'")
                .hasMessageContaining("action 'keep'").hasMessageContaining("'Xyzzy' não encontrado");
    }

    @Test
    void objectWithoutPositionIsReportedAndNoRequestIsMade() throws Exception {
        UUID orphan = jdbc.sql("INSERT INTO celestial_object (slug, display_name) VALUES ('sem-posicao', 'Sem posição') RETURNING id")
                .query(UUID.class).single();
        ImageRunResult r = cutouts.run(targets.load(List.of(orphan)), RunTrigger.MANUAL, false);
        assertThat(r.outcomes()).extracting(ImageOutcome::kind).containsExactly(ImageOutcome.Kind.NO_POSITION);
        assertThat(fixtureCutouts().cutoutCalls()).isZero();
        assertThat(Files.isDirectory(imageStorage())).isTrue();
    }

    @Test
    void objectWithoutFixtureCutoutFailsCleanlyAndRunIsPartial() {
        UUID m81 = jdbc.sql("SELECT id FROM celestial_object WHERE slug = 'messier-81'").query(UUID.class).single();
        ImageRunResult r = cutouts.run(targets.load(List.of(m51, m81)), RunTrigger.MANUAL, false);
        Map<String, ImageOutcome.Kind> kinds = new java.util.HashMap<>();
        r.outcomes().forEach(o -> kinds.put(o.slug(), o.kind()));
        assertThat(kinds).containsEntry("messier-51", ImageOutcome.Kind.STORED)
                .containsEntry("messier-81", ImageOutcome.Kind.SOURCE_QUERY_FAILED);
        assertThat(r.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(images.findByObject(m81)).isEmpty();
    }

    // ── Objeto grande: M31 (ang_size_major 199,53' no SIMBAD real) ─────────────────────────────

    private UUID seedSamples() {
        pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.SEED, Samples.QUERIES));
        return jdbc.sql("SELECT id FROM celestial_object WHERE slug = 'messier-31'").query(UUID.class).single();
    }

    private Double coverageInDb(UUID objectId, String sourceImageId) {
        return jdbc.sql("SELECT coverage_ratio FROM object_image WHERE object_id = :o AND source_image_id = :s")
                .param("o", objectId).param("s", sourceImageId).query(Double.class).single();
    }

    private CurationFile curation(String... lines) {
        return CurationFile.parse("object_query,provider,image_id,action,note\n" + String.join("\n", lines) + "\n",
                service::resolveExact);
    }

    private ImageRunResult hubble(UUID objectId, CurationFile curation) {
        return esa.run(ImagesConfig.esaSites(props).get(0), ImageSource.ESA_HUBBLE, targets.load(List.of(objectId)),
                curation, RunTrigger.MANUAL, false);
    }

    private String errorSummary(ImageRunResult r) {
        return jdbc.sql("SELECT error_summary FROM ingestion_run WHERE id = :id").param("id", r.runId())
                .query(String.class).single();
    }

    @Test
    void m31CutoutIsHeroBecauseHubbleFrameCoversFarLessAndHeic1502aGoesToGallery() {
        UUID m31 = seedSamples();

        ImageIngestionReport report = service.run(new ImageIngestionRequest(
                Set.of(ImageSource.CUTOUT, ImageSource.ESA_HUBBLE), List.of(m31), RunTrigger.MANUAL, false));

        List<ObjectImageRow> rows = images.findByObject(m31);
        assertThat(rows).extracting(ObjectImageRow::sourceImageId).containsExactly("CDS/P/DSS2/color", "heic1502a");
        ObjectImageRow hero = rows.get(0);
        assertThat(hero.hero()).isTrue();
        assertThat(hero.kind()).isEqualTo(ImageKind.SURVEY_CUTOUT);
        // o recorte gravado e exatamente o que o planner pede para M31 (o cliente de fixture exige URL igual)
        assertThat(hero.imageUrl()).isEqualTo(FixtureHips2FitsClient.M31_URL);
        assertThat(hero.fovDeg()).isCloseTo(4.156875, within(1e-9));
        assertThat(hero.widthPx()).isEqualTo(800);
        assertThat(hero.heightPx()).isEqualTo(600);
        assertThat(hero.contentSha256())
                .isEqualTo(ImageFileStore.sha256Hex(Fixtures.readBytes("hips2fits/m31_dss2_800x600.jpg")));
        assertThat(hero.creditVerbatim()).isEqualTo("Digitized Sky Survey - STScI/NASA, Colored & Healpixed by CDS");
        assertThat(hero.rightsText()).isEqualTo("HiPS © CNRS/Unistra — ODbL-1.0");
        assertThat(hero.providerShort()).isEqualTo("DSS2 via CDS");

        ObjectImageRow gallery = rows.get(1);
        assertThat(gallery.hero()).isFalse();
        assertThat(gallery.galleryRank()).isGreaterThan(hero.galleryRank());
        assertThat(gallery.sourceCode()).isEqualTo("ESA_HUBBLE");
        assertThat(gallery.curatedBy()).isEqualTo("automatic");
        assertThat(gallery.widthPx()).isEqualTo(1280);
        assertThat(gallery.heightPx()).isEqualTo(409); // lido do JPEG real (a AVM diz 410)
        // PHAT: menor lado 22230 px x 1,38979e-5 deg/px = 18,537' / 199,53' = 0,0929 (< 0,5: "cobre bem menos")
        assertThat(gallery.coverageRatio()).isCloseTo(0.0929, within(0.0001));
        assertThat(coverageInDb(m31, "heic1502a")).isCloseTo(0.0929, within(0.0001));
        assertThat(report.runStatuses()).containsEntry(ImageSource.CUTOUT, RunStatus.OK);
    }

    // ── Curadoria include/hero (fixtures AVM reais) ────────────────────────────────────────────

    @Test
    void curatedHeroBeatsTheCutoutEvenWhenItCoversFarLess() {
        UUID m31 = seedSamples();
        cutouts.run(targets.load(List.of(m31)), RunTrigger.MANUAL, false);

        hubble(m31, curation("M31,HUBBLE,heic1502a,hero,PHAT"));

        List<ObjectImageRow> rows = images.findByObject(m31);
        assertThat(rows).extracting(ObjectImageRow::sourceImageId).containsExactly("heic1502a", "CDS/P/DSS2/color");
        ObjectImageRow hero = rows.get(0);
        assertThat(hero.hero()).isTrue();
        assertThat(hero.curatedHero()).isTrue();
        assertThat(hero.curatedBy()).isEqualTo("manual");
        assertThat(hero.coverageRatio()).isLessThan(0.5); // a cobertura baixa nao tira o hero curado
        assertThat(hero.matchBasis()).startsWith("curadoria manual; ");
        assertThat(rows.get(1).hero()).isFalse();
    }

    @Test
    void curatedIncludeIsStoredAsManualButDoesNotTakeTheHero() {
        UUID m31 = seedSamples();
        cutouts.run(targets.load(List.of(m31)), RunTrigger.MANUAL, false);

        hubble(m31, curation("M31,HUBBLE,heic1502a,include,"));

        List<ObjectImageRow> rows = images.findByObject(m31);
        assertThat(rows).extracting(ObjectImageRow::sourceImageId).containsExactly("CDS/P/DSS2/color", "heic1502a");
        assertThat(rows.get(1).curatedBy()).isEqualTo("manual");
        assertThat(rows.get(1).curatedHero()).isFalse();
    }

    @Test
    void curatedIdsAreFetchedWithoutSearchButStillNeedSubjectNameAndPosition() {
        seedSamples(); // traz Betelgeuse: sem designacao Messier/NGC, nao ha termo de busca
        UUID betelgeuse = jdbc.sql("SELECT object_id FROM object_identifier WHERE identifier = '* alf Ori'")
                .query(UUID.class).single();

        ImageRunResult r = hubble(betelgeuse, curation(
                "Betelgeuse,HUBBLE,opo9604b,include,quadro de 0.34 arcsec",
                "Betelgeuse,HUBBLE,heic0601a,include,e a nebulosa de Orion"));

        assertThat(fixtureEsaClient().searchCalls()).isZero();
        assertThat(fixtureEsaClient().avmCalls()).isEqualTo(2);
        assertThat(fixtureEsaClient().downloads()).isZero();
        assertThat(r.outcomes()).extracting(ImageOutcome::kind).containsExactly(ImageOutcome.Kind.NO_VALID_CANDIDATE);
        assertThat(images.findByObject(betelgeuse)).isEmpty();
        String summary = errorSummary(r);
        assertThat(summary).contains("opo9604b: centro da imagem a").contains("fora do quadro");
        assertThat(summary).contains("heic0601a: Subject.Name").contains("não casa");
    }

    @Test
    void curatedIdOutsideTheSearchResultsIsFetchedAndJudgedByTheSameRules() {
        // heic1502a (M31) nao esta entre os resultados reais de "Messier 51": a AVM so e pedida pela curadoria
        assertThat(Fixtures.read("esa/hubble_search_messier_51.html")).doesNotContain("heic1502a");

        ImageRunResult r = hubble(m51, curation("M51,HUBBLE,heic1502a,include,M31 de proposito"));

        assertThat(fixtureEsaClient().searchCalls()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM raw_payload WHERE dataset = 'esa_avm_json' AND query_text = 'heic1502a'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(images.findByObject(m51)).extracting(ObjectImageRow::sourceImageId).containsExactly("heic0506a");
        assertThat(errorSummary(r)).contains("messier-51 / heic1502a: Subject.Name");
    }
}
