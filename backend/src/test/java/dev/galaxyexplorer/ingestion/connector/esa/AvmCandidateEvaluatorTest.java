package dev.galaxyexplorer.ingestion.connector.esa;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.catalog.image.HeroPolicy;
import dev.galaxyexplorer.catalog.image.ImageKind;
import dev.galaxyexplorer.ingestion.connector.image.ImageCandidate;
import dev.galaxyexplorer.ingestion.connector.image.ImageTarget;
import dev.galaxyexplorer.support.Fixtures;
import dev.galaxyexplorer.support.RealTargets;

/** Todas as AVM e todos os objetos vem de respostas reais gravadas (fixtures/esa, fixtures/simbad). */
class AvmCandidateEvaluatorTest {

    static final EsaSite HUBBLE = new EsaSite(EsaProvider.HUBBLE, URI.create("https://esahubble.org"),
            URI.create("https://cdn.esahubble.org"));
    static final EsaSite WEBB = new EsaSite(EsaProvider.WEBB, URI.create("https://esawebb.org"),
            URI.create("https://cdn.esawebb.org"));

    static AvmRecord avm(String site, String id) {
        return AvmJsonParser.parse(id, Fixtures.read("esa/" + site + "_avm_" + id + ".json"));
    }

    static ImageCandidate accepted(AvmCandidateEvaluator.Evaluation e) {
        assertThat(e).isInstanceOf(AvmCandidateEvaluator.Accepted.class);
        return ((AvmCandidateEvaluator.Accepted) e).candidate();
    }

    static String rejected(AvmCandidateEvaluator.Evaluation e) {
        assertThat(e).isInstanceOf(AvmCandidateEvaluator.Rejected.class);
        return ((AvmCandidateEvaluator.Rejected) e).reason();
    }

    @Test
    void heic0506aIsTheWhirlpoolWithLiteralCreditAndWholeObjectInFrame() {
        ImageTarget m51 = RealTargets.of("cz", "M  51");
        ImageCandidate c = accepted(AvmCandidateEvaluator.evaluate(HUBBLE, avm("hubble", "heic0506a"), m51, false, false));

        assertThat(c.source()).isEqualTo(SourceCode.ESA_HUBBLE);
        assertThat(c.kind()).isEqualTo(ImageKind.PROCESSED_COMPOSITE);
        assertThat(c.creditVerbatim())
                .isEqualTo("NASA, ESA, S. Beckwith (STScI), and The Hubble Heritage Team (STScI/AURA)");
        assertThat(c.title()).isEqualTo("Out of this whirl: The Whirlpool Galaxy (M51) and companion galaxy");
        assertThat(c.licenseCode()).isEqualTo("CC-BY-4.0");
        assertThat(c.rightsText()).isEqualTo("Creative Commons Attribution 4.0 International License");
        assertThat(c.mission()).isEqualTo("Hubble Space Telescope");
        assertThat(c.instrument()).isEqualTo("ACS");
        assertThat(c.wavelength()).isEqualTo("Optical, Infrared");
        assertThat(c.releaseDate()).isEqualTo(LocalDate.of(2005, 4, 25));
        assertThat(c.pageUrl()).isEqualTo("https://esahubble.org/images/heic0506a/");
        assertThat(c.downloadUrl()).hasToString("https://cdn.esahubble.org/archives/images/screen/heic0506a.jpg");
        assertThat(c.matchBasis()).contains("'Messier 51' = identificador 'M  51'").contains("objeto dentro do quadro");
        // quadro 9,56' x 6,64' contra ang_size_major 9,04' do SIMBAD: menor lado = 73% -> ESA pode ser hero
        assertThat(c.coverageRatio()).isCloseTo(6.6357 / 9.04379, org.assertj.core.data.Offset.offset(1e-3));
        assertThat(HeroPolicy.coversFarLess(c.coverageRatio())).isFalse();
    }

    @Test
    void weic2216bIsAcceptedForM16AsPartOfTheObjectButCoversFarLess() {
        ImageTarget m16 = RealTargets.of("m16", "M  16");
        ImageCandidate c = accepted(AvmCandidateEvaluator.evaluate(WEBB, avm("webb", "weic2216b"), m16, false, false));

        assertThat(c.source()).isEqualTo(SourceCode.ESA_WEBB);
        assertThat(c.mission()).isEqualTo("James Webb Space Telescope");
        assertThat(c.creditVerbatim()).isEqualTo("NASA, ESA, CSA, STScI; J. DePasquale, A. Koekemoer, A. Pagan (STScI).");
        assertThat(c.separationArcmin()).isBetween(4.0, 4.6);
        assertThat(c.matchBasis()).contains("fora do quadro").contains("dentro da extensão do objeto (raio 40′)");
        // 4,21' / 80' = 5% -> o recorte vira hero (ADENDO)
        assertThat(HeroPolicy.coversFarLess(c.coverageRatio())).isTrue();
    }

    @Test
    void largeObjectsFromRealDataCoverFarLessAndM42IsNamedByOrionNebula() {
        ImageCandidate m31 = accepted(AvmCandidateEvaluator.evaluate(HUBBLE, avm("hubble", "heic1502a"),
                RealTargets.of("sample", "M  31"), false, false));
        assertThat(m31.coverageRatio()).isBetween(0.09, 0.10); // 18,5' / 199,53'
        assertThat(HeroPolicy.coversFarLess(m31.coverageRatio())).isTrue();

        ImageCandidate m42 = accepted(AvmCandidateEvaluator.evaluate(HUBBLE, avm("hubble", "heic0601a"),
                RealTargets.of("sample", "M  42"), false, false));
        assertThat(m42.coverageRatio()).isBetween(0.45, 0.46); // 30,0' / 66'
        assertThat(HeroPolicy.coversFarLess(m42.coverageRatio())).isTrue();
    }

    @Test
    void imageOfAnotherObjectFromTheSearchIsRejectedByName() {
        // heic0506b (Eagle Nebula / Messier 16) e o 3o resultado da busca "Messier 1" (a busca e "contem")
        String reason = rejected(AvmCandidateEvaluator.evaluate(HUBBLE, avm("hubble", "heic0506b"),
                RealTargets.of("sample", "M   1"), false, false));
        assertThat(reason).contains("Subject.Name [Eagle Nebula, Messier 16] não casa");
    }

    @Test
    void groundBasedContextImageIsRejectedAutomatically() {
        String reason = rejected(AvmCandidateEvaluator.evaluate(HUBBLE, avm("hubble", "heic0506c"),
                RealTargets.of("cz", "M  51"), false, false));
        assertThat(reason).contains("Digitized Sky Survey 2").contains("Hubble Space Telescope");
    }

    @Test
    void artworkIsRejectedAutomaticallyButCuratedAsIllustrationThatCanNeverBeHero() {
        ImageTarget betelgeuse = RealTargets.of("sample", "* alf Ori");
        AvmRecord artwork = avm("hubble", "heic2014a");

        assertThat(rejected(AvmCandidateEvaluator.evaluate(HUBBLE, artwork, betelgeuse, false, false)))
                .contains("Type 'Artwork'");

        ImageCandidate curated = accepted(AvmCandidateEvaluator.evaluate(HUBBLE, artwork, betelgeuse, true, true));
        assertThat(curated.kind()).isEqualTo(ImageKind.ARTIST_IMPRESSION);
        assertThat(curated.curatedHero()).isFalse(); // pedido de hero ignorado: ilustracao nunca e hero
        assertThat(curated.creditVerbatim()).isEqualTo("ESO, ESA/Hubble, M. Kornmesser");
        assertThat(curated.title()).isEqualTo("Betelgeuse’s Dust Cloud (Artist’s Impression)");
        assertThat(curated.matchBasis()).contains("curadoria manual").contains("sem coordenadas na AVM");
    }

    @Test
    void collageIsNeverAccepted() {
        ImageTarget betelgeuse = RealTargets.of("sample", "* alf Ori");
        assertThat(rejected(AvmCandidateEvaluator.evaluate(HUBBLE, avm("hubble", "opo9604a"), betelgeuse, true, false)))
                .contains("Type 'Collage'");
    }

    @Test
    void tinyFrameAwayFromTheStarIsRejectedSpatially() {
        // opo9604b: quadro de 0,34" cujo centro fica a ~2" da posicao do SIMBAD; estrela sem tamanho angular
        String reason = rejected(AvmCandidateEvaluator.evaluate(HUBBLE, avm("hubble", "opo9604b"),
                RealTargets.of("sample", "* alf Ori"), false, false));
        assertThat(reason).contains("fora do quadro").contains("objeto sem tamanho angular");
    }

    @Test
    void imageFarFromTheObjectIsRejected() {
        // alteracao EM MEMORIA declarada: a posicao real de M51 trocada pela de M31 (mesmos identificadores)
        ImageTarget m51 = RealTargets.of("cz", "M  51");
        ImageTarget m31 = RealTargets.of("sample", "M  31");
        ImageTarget moved = new ImageTarget(m51.objectId(), m51.slug(), m51.displayName(), m51.identifiers(),
                m31.raDeg(), m31.decDeg(), "SIMBAD", m51.angSizeMajorArcmin(), 1L, "SIMBAD");
        assertThat(rejected(AvmCandidateEvaluator.evaluate(HUBBLE, avm("hubble", "heic0506a"), moved, false, false)))
                .contains("fora do quadro").contains("fora da extensão do objeto");
    }
}
