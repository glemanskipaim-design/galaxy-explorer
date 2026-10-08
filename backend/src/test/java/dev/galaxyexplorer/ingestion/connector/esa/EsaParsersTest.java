package dev.galaxyexplorer.ingestion.connector.esa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.support.Fixtures;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Busca HTML, valores AVM, nome e licenca — sobre as respostas reais (fixtures/esa). */
class EsaParsersTest {

    private static JsonNode json(String s) {
        return JsonMapper.builder().build().readTree(s);
    }

    @Test
    void hubbleSearchIdsComeFromTheScriptBlockInPageOrder() {
        List<String> ids = EsaSearchHtmlParser.imageIds(Fixtures.read("esa/hubble_search_messier_51.html"));
        assertThat(ids).startsWith("heic0506a").contains("heic0506c", "ann0821").doesNotHaveDuplicates()
                .allMatch(id -> EsaSite.IMAGE_ID.matcher(id).matches());
    }

    @Test
    void shortTermMissesTheMainImage() {
        // fato que motivou o termo "Messier N": "M 51" nao devolve heic0506a
        assertThat(EsaSearchHtmlParser.imageIds(Fixtures.read("esa/hubble_search_m_51.html")))
                .hasSize(11).doesNotContain("heic0506a");
    }

    @Test
    void webbIndentedBlockAndNoResultsPage() {
        assertThat(EsaSearchHtmlParser.imageIds(Fixtures.read("esa/webb_search_messier_16.html")))
                .containsExactly("weic2218a", "weic2216b", "weic2216a");
        assertThat(EsaSearchHtmlParser.imageIds(Fixtures.read("esa/webb_search_m_16_noresults.html"))).isEmpty();
    }

    @Test
    void unknownHtmlIsAnErrorNotZeroResults() {
        // alteracao em memoria declarada: remove o bloco e a frase de "sem resultados" do HTML real
        String html = Fixtures.read("esa/hubble_search_messier_51.html").replace("var images = [", "var imgs = [");
        assertThatThrownBy(() -> EsaSearchHtmlParser.imageIds(html)).isInstanceOf(SourceQueryException.class)
                .hasMessageContaining("formato desconhecido");
    }

    @Test
    void avmTextUndoesPythonBytesReprWithUtf8Escapes() {
        AvmRecord c = AvmJsonParser.parse("heic0506c", Fixtures.read("esa/hubble_avm_heic0506c.json"));
        assertThat(c.credit()).startsWith("ESA/Hubble and Digitized Sky Survey 2.");
        assertThat(AvmValues.text(json("\"b'Webb\\\\xe2\\\\x80\\\\x99s view'\""))).isEqualTo("Webb’s view");
        // forma b"..." (texto com apostrofo), vista em Description de heic0506a
        assertThat(AvmValues.decode("b\"spiral galaxy's arms\"")).isEqualTo("spiral galaxy's arms");
        assertThat(AvmValues.text(json("\"b''\""))).isNull();
        assertThat(AvmValues.text(json("\"Observation\""))).isEqualTo("Observation");
    }

    @Test
    void avmFormsNotSeenInFixturesAreRefused() {
        assertThatThrownBy(() -> AvmValues.list(json("\"['a', 'b']\""))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AvmValues.decode("b'bad \\q escape'")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AvmValues.decode("b'\\xff'")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void artworkHasEmptySpatialListsAndNullFacility() {
        AvmRecord r = AvmJsonParser.parse("heic2014a", Fixtures.read("esa/hubble_avm_heic2014a.json"));
        assertThat(r.type()).isEqualTo("Artwork");
        assertThat(r.facilities()).isEmpty();
        assertThat(r.refRaDeg()).isNull();
        assertThat(r.hasFrame()).isFalse();
    }

    @Test
    void avmIdMustBeTheRequestedOne() {
        assertThatThrownBy(() -> AvmJsonParser.parse("heic0506b", Fixtures.read("esa/hubble_avm_heic0506a.json")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("heic0506a");
    }

    @Test
    void subjectNamesMatchWithTheCatalogNormalizerPlusMessierAlias() {
        assertThat(SubjectNameMatcher.match(List.of("Messier 51"), List.of("NGC  5194", "M  51"))).isPresent();
        assertThat(SubjectNameMatcher.match(List.of("Whirlpool Galaxy"), List.of("NAME Whirlpool Galaxy"))).isPresent();
        assertThat(SubjectNameMatcher.match(List.of("Messier 101"), List.of("M   1"))).isEmpty();
        assertThat(SubjectNameMatcher.match(List.of("Messier 16"), List.of("M   1"))).isEmpty();
    }

    @Test
    void onlyTheExactRightsTextBecomesALicense() {
        assertThat(EsaLicenseRule.licenseCode("Creative Commons Attribution 4.0 International License"))
                .contains("CC-BY-4.0");
        assertThat(EsaLicenseRule.licenseCode("Creative Commons Attribution 4.0")).isEmpty();
        assertThat(EsaLicenseRule.licenseCode(null)).isEmpty();
    }

    @Test
    void siteUrlsFollowTheVerifiedForms() {
        EsaSite hubble = AvmCandidateEvaluatorTest.HUBBLE;
        assertThat(hubble.searchUrl("Messier 51"))
                .hasToString("https://esahubble.org/images/archive/search/?adv=&subject_name=Messier+51");
        assertThat(hubble.avmUrl("heic0506a")).hasToString("https://esahubble.org/images/heic0506a/api/json/");
        assertThat(AvmCandidateEvaluatorTest.WEBB.screenUrl("weic2216b"))
                .hasToString("https://cdn.esawebb.org/archives/images/screen/weic2216b.jpg");
        assertThatThrownBy(() -> hubble.avmUrl("../etc")).isInstanceOf(IllegalArgumentException.class);
    }
}
