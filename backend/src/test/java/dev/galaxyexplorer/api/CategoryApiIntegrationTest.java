package dev.galaxyexplorer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.catalog.category.CategoryDecision;
import dev.galaxyexplorer.catalog.category.CategoryRepository;
import dev.galaxyexplorer.catalog.category.CategoryResolver;
import dev.galaxyexplorer.catalog.category.CategoryRow;
import dev.galaxyexplorer.catalog.category.TypeMappingRule;
import dev.galaxyexplorer.catalog.object.CelestialObjectRepository;
import dev.galaxyexplorer.catalog.object.DisplayNameRule;
import dev.galaxyexplorer.catalog.object.SlugFactory;
import dev.galaxyexplorer.catalog.source.SourceRepository;
import dev.galaxyexplorer.ingestion.connector.tap.TapJsonParser;
import dev.galaxyexplorer.ingestion.connector.tap.TapRow;
import dev.galaxyexplorer.support.Fixtures;
import dev.galaxyexplorer.ingestion.pipeline.IngestionPipeline;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRequest;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;
import dev.galaxyexplorer.support.Samples;

/**
 * Endpoints de categoria. Assercoes robustas ao mapeamento de tipos: nao fixam contagens de
 * casos que dependem da regra de classificacao (essas ja tem teste proprio no Bloco 1).
 */
class CategoryApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    IngestionPipeline pipeline;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    CategoryResolver resolver;
    @Autowired
    SourceRepository sources;
    @Autowired
    CelestialObjectRepository objects;

    @BeforeEach
    void ingestSample() {
        pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.SEED, Samples.QUERIES));
    }

    @Test
    void listsOnlyRootCategoriesIncludingEmptyOnes() throws Exception {
        String body = mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[*].code", Matchers.hasItems("star", "galaxy", "nebula", "cluster",
                        "black_hole", "exoplanet")))
                // subcategorias nunca aparecem na lista de raizes
                .andExpect(jsonPath("$.categories[*].code", Matchers.not(Matchers.hasItem("galaxy_spiral"))))
                .andExpect(jsonPath("$.categories[?(@.code == 'galaxy')].namePt").value("Galáxia"))
                .andExpect(jsonPath("$.categories[?(@.code == 'galaxy')].nameEn").value("Galaxy"))
                .andReturn().getResponse().getContentAsString();
        int roots = jdbc.sql("SELECT count(*) FROM object_category WHERE parent_id IS NULL").query(Integer.class)
                .single();
        List<String> codes = JsonPath.read(body, "$.categories[*].code");
        assertThat(codes).hasSize(roots);
    }

    @Test
    void rootCountsPartitionTheClassifiedObjects() throws Exception {
        String body = mvc.perform(get("/api/v1/categories")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> counts = JsonPath.read(body, "$.categories[*].objectCount");
        long sum = counts.stream().mapToLong(Number::longValue).sum();
        long classified = jdbc.sql("SELECT count(*) FROM celestial_object WHERE category_id IS NOT NULL")
                .query(Long.class).single();
        assertThat(classified).isPositive();
        assertThat(sum).isEqualTo(classified);
    }

    @Test
    void countsReflectTheSample() throws Exception {
        String body = mvc.perform(get("/api/v1/categories")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(countOf(body, "star")).isGreaterThanOrEqualTo(1); // Betelgeuse
        // Sgr A* e classificado como fonte de raios X (descoberta 2 da Fase 0), nao buraco negro
        assertThat(countOf(body, "black_hole")).isZero();
        assertThat(countOf(body, "exoplanet")).isZero();
    }

    @Test
    void rootListingIncludesObjectsOfSubcategories() throws Exception {
        mvc.perform(get("/api/v1/categories/galaxy/objects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.code").value("galaxy"))
                .andExpect(jsonPath("$.category.namePt").value("Galáxia"))
                .andExpect(jsonPath("$.limit").value(24))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.items[*].slug", Matchers.hasItem("messier-31")))
                // M31 e da filha galaxy_spiral: prova que a subarvore funciona
                .andExpect(jsonPath("$.items[?(@.slug == 'messier-31')].categoryCode").value("galaxy_spiral"))
                .andExpect(jsonPath("$.items[?(@.slug == 'messier-31')].categoryName").value("Galáxia espiral"))
                .andExpect(jsonPath("$.items[?(@.slug == 'messier-31')].displayName").value("M 31"));
    }

    @Test
    void subcategoryCanBeListedDirectly() throws Exception {
        mvc.perform(get("/api/v1/categories/galaxy_spiral/objects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.code").value("galaxy_spiral"))
                .andExpect(jsonPath("$.items[*].slug", Matchers.hasItem("messier-31")))
                .andExpect(jsonPath("$.items[*].categoryCode", Matchers.everyItem(Matchers.is("galaxy_spiral"))));
    }

    @Test
    void paginationKeepsTotalAndSlices() throws Exception {
        String all = mvc.perform(get("/api/v1/categories/cluster/objects")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int total = JsonPath.read(all, "$.total");
        List<String> slugs = JsonPath.read(all, "$.items[*].slug");
        assertThat(total).isGreaterThanOrEqualTo(2).isEqualTo(slugs.size());

        mvc.perform(get("/api/v1/categories/cluster/objects").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(total))
                .andExpect(jsonPath("$.items", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value(slugs.get(0)));
        mvc.perform(get("/api/v1/categories/cluster/objects").param("limit", "1").param("offset", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value(slugs.get(1)));
        mvc.perform(get("/api/v1/categories/cluster/objects").param("offset", String.valueOf(total + 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(total))
                .andExpect(jsonPath("$.items", Matchers.hasSize(0)));
    }

    @Test
    void listingUsesNaturalOrder() throws Exception {
        // O sample nao tem dois Messier cuja ordem natural difira da lexicografica dentro de uma
        // mesma categoria; a lista inteira em ordem natural e conferida com o comparador abaixo.
        String body = mvc.perform(get("/api/v1/categories/cluster/objects")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(body, "$.items[*].displayName");
        assertThat(names).contains("M 13", "M 54").isSortedAccordingTo(CategoryApiIntegrationTest::naturalCompare);
        assertThat(names.indexOf("M 13")).isLessThan(names.indexOf("M 54"));
    }

    @Test
    void listingOrderIsNaturalWithRealSingleDigitMessier() throws Exception {
        // O sample so tem Messier de 2 digitos por categoria (ordem natural = lexicografica). Para o
        // teste decidir de verdade, entram M 2, M 9 e M 10 a partir da fixture REAL messier_types.json
        // (main_id, otype e morph_type do SIMBAD), classificados pelas regras reais do banco.
        // Sem COLLATE natural_sort o ORDER BY daria M 10, M 13, M 2, M 9 e este teste falha.
        insertRealMessier(Set.of("M 2", "M 9", "M 10"));
        String body = mvc.perform(get("/api/v1/categories/cluster/objects").param("limit", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(body, "$.items[*].displayName");
        assertThat(names).contains("M 2", "M 9", "M 10", "M 13");
        assertThat(names.indexOf("M 2")).isLessThan(names.indexOf("M 9"));
        assertThat(names.indexOf("M 9")).isLessThan(names.indexOf("M 10"));
        assertThat(names.indexOf("M 10")).isLessThan(names.indexOf("M 13"));
    }

    /** Insere so a identidade e a classificacao (sem medicoes) de Messier reais da fixture do Bloco 1. */
    private void insertRealMessier(Set<String> wanted) {
        List<TypeMappingRule> rules = categoryRepository.findRules(sources.getByCode(SourceCode.SIMBAD).id());
        CategoryRow other = categoryRepository.findByCode("other").orElseThrow();
        int inserted = 0;
        for (TapRow row : TapJsonParser.parse(Fixtures.read("simbad/messier_types.json")).rows()) {
            String name = DisplayNameRule.choose(row.text("main_id").orElse(null),
                    List.of(row.text("messier_id").orElseThrow()));
            if (!wanted.contains(name)) {
                continue;
            }
            CategoryDecision decision = resolver.resolve("SIMBAD", row.text("otype").orElseThrow(),
                    row.text("morph_type").orElse(null), rules, other);
            assertThat(decision.categoryCode()).as(name).startsWith("cluster");
            objects.insert(SlugFactory.baseSlug(name), name, decision.categoryId(), decision.basis());
            inserted++;
        }
        assertThat(inserted).isEqualTo(wanted.size());
    }

    @Test
    void naturalSortCollationOrdersNumbersNumerically() {
        // Prova direta da collation da V7, com rotulos (nao objetos): lexicografico daria M 100, M 13, M 9.
        List<String> ordered = jdbc.sql("""
                SELECT v FROM (VALUES ('M 100'), ('M 9'), ('M 13')) AS t(v) ORDER BY v COLLATE natural_sort
                """).query(String.class).list();
        assertThat(ordered).containsExactly("M 9", "M 13", "M 100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"nao_existe", "GALAXY", "a-b", "galaxy1"})
    void unknownOrMalformedCodeIsNotFound(String code) throws Exception {
        mvc.perform(get("/api/v1/categories/" + code + "/objects"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").isString());
    }

    @Test
    void codeWithSlashIsNotFound() throws Exception {
        // "a/b" nunca chega ao controller como um segmento so: o Spring responde 404 sem o corpo padrao.
        mvc.perform(get("/api/v1/categories/a/b/objects")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"limit,0", "limit,101", "offset,-1", "offset,10001"})
    void outOfRangePagingIsValidationError(String param, String value) throws Exception {
        mvc.perform(get("/api/v1/categories/galaxy/objects").param(param, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value(param));
    }

    private static long countOf(String body, String code) {
        List<Number> values = JsonPath.read(body, "$.categories[?(@.code == '" + code + "')].objectCount");
        assertThat(values).as("categoria %s presente", code).hasSize(1);
        return values.get(0).longValue();
    }

    /** "M 9" &lt; "M 13": compara trechos numericos pelo valor. */
    private static int naturalCompare(String a, String b) {
        String[] pa = a.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        String[] pb = b.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            int c = Character.isDigit(pa[i].charAt(0)) && Character.isDigit(pb[i].charAt(0))
                    ? Long.compare(Long.parseLong(pa[i]), Long.parseLong(pb[i]))
                    : pa[i].compareToIgnoreCase(pb[i]);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(pa.length, pb.length);
    }
}
