package dev.galaxyexplorer.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.category.CategoryRepository;
import dev.galaxyexplorer.catalog.category.CategoryResolver;
import dev.galaxyexplorer.catalog.category.CategoryRow;
import dev.galaxyexplorer.catalog.category.TypeMappingRule;
import dev.galaxyexplorer.catalog.source.SourceRepository;
import dev.galaxyexplorer.catalog.source.SourceRow;
import dev.galaxyexplorer.ingestion.connector.tap.TapJsonParser;
import dev.galaxyexplorer.ingestion.connector.tap.TapResult;
import dev.galaxyexplorer.ingestion.connector.tap.TapRow;
import dev.galaxyexplorer.support.Fixtures;

class ReferenceDataIntegrationTest extends IntegrationTestBase {

    /** Otypes Messier deliberadamente sem regra: '?' (M40, objeto de natureza desconhecida) e 'err' (M73). */
    private static final Set<String> ACCEPTED_AS_OTHER = Set.of("?", "err");

    @Autowired
    SourceRepository sources;
    @Autowired
    CategoryRepository categories;
    @Autowired
    CategoryResolver resolver;

    private List<TypeMappingRule> simbadRules() {
        return categories.findRules(sources.getByCode(SourceCode.SIMBAD).id());
    }

    @Test
    void sourcesHaveHomepageAndAcknowledgment() {
        SourceRow simbad = sources.getByCode(SourceCode.SIMBAD);
        assertThat(simbad.homepageUrl()).isEqualTo("https://simbad.cds.unistra.fr/simbad/");
        assertThat(simbad.acknowledgment())
                .startsWith("This research has made use of the SIMBAD database, operated at CDS, Strasbourg, France")
                .contains("Wenger et al.");
        SourceRow exo = sources.getByCode(SourceCode.NASA_EXO);
        assertThat(exo.homepageUrl()).isEqualTo("https://exoplanetarchive.ipac.caltech.edu/");
        assertThat(exo.acknowledgment()).startsWith("This research has made use of the NASA Exoplanet Archive");
    }

    @Test
    void categoryHierarchy() {
        CategoryRow galaxy = categories.findByCode("galaxy").orElseThrow();
        assertThat(categories.findByCode("galaxy_spiral").orElseThrow().parentId()).isEqualTo(galaxy.id());
        CategoryRow agn = categories.findByCode("agn").orElseThrow();
        assertThat(categories.findByCode("quasar").orElseThrow().parentId()).isEqualTo(agn.id());
        assertThat(categories.findByCode("other").orElseThrow().parentId()).isNull();
    }

    @Test
    void everyMappedOtypeExistsInRealOtypedef() {
        TapResult otypedef = TapJsonParser.parse(Fixtures.read("simbad/otypedef_all.json"));
        Set<String> real = new HashSet<>();
        otypedef.rows().forEach(r -> real.add(r.text("otype").orElseThrow()));
        assertThat(real).hasSizeGreaterThan(200);
        Set<String> mapped = new HashSet<>();
        simbadRules().forEach(r -> mapped.add(r.sourceType()));
        assertThat(real).containsAll(mapped);
    }

    @Test
    void everyMessierOtypeHasRuleOrIsExplicitlyAcceptedAsOther() {
        Set<String> mapped = new HashSet<>();
        simbadRules().forEach(r -> mapped.add(r.sourceType()));
        TapResult messier = TapJsonParser.parse(Fixtures.read("simbad/messier_types.json"));
        assertThat(messier.size()).isEqualTo(110);
        Set<String> unmapped = new HashSet<>();
        for (TapRow row : messier.rows()) {
            String otype = row.text("otype").orElseThrow();
            if (!mapped.contains(otype)) {
                unmapped.add(otype);
            }
        }
        assertThat(unmapped).isEqualTo(ACCEPTED_AS_OTHER);
    }

    @Test
    void stellarOtypesFromV5() {
        List<TypeMappingRule> rules = simbadRules();
        CategoryRow other = categories.findByCode("other").orElseThrow();
        // Vega (dS*, delta Sct Variable) e TRAPPIST-1 (LM*, Low-mass Star) caiam em 'other' antes da V5
        assertThat(resolver.resolve("SIMBAD", "dS*", null, rules, other).categoryCode()).isEqualTo("star_variable");
        assertThat(resolver.resolve("SIMBAD", "LM*", null, rules, other).categoryCode()).isEqualTo("star_dwarf");
        assertThat(resolver.resolve("SIMBAD", "s*r", null, rules, other).categoryCode()).isEqualTo("star_supergiant");
        assertThat(resolver.resolve("SIMBAD", "**", null, rules, other).categoryCode()).isEqualTo("star_binary");
        assertThat(resolver.resolve("SIMBAD", "WR*", null, rules, other).categoryCode()).isEqualTo("star_wolf_rayet");
        assertThat(resolver.resolve("SIMBAD", "Psr", null, rules, other).categoryCode()).isEqualTo("star_pulsar");
        assertThat(resolver.resolve("SIMBAD", "BD*", null, rules, other).categoryCode()).isEqualTo("brown_dwarf");
        // candidato a tipo nao e o tipo
        assertThat(resolver.resolve("SIMBAD", "WD?", null, rules, other).categoryCode()).isEqualTo("other");
        // hierarquia
        int star = categories.findByCode("star").orElseThrow().id();
        for (String code : List.of("star_dwarf", "star_giant", "star_supergiant", "star_variable", "star_binary",
                "star_young", "star_wolf_rayet", "star_white_dwarf", "star_neutron")) {
            assertThat(categories.findByCode(code).orElseThrow().parentId()).as(code).isEqualTo(star);
        }
        assertThat(categories.findByCode("star_pulsar").orElseThrow().parentId())
                .isEqualTo(categories.findByCode("star_neutron").orElseThrow().id());
        assertThat(categories.findByCode("brown_dwarf").orElseThrow().parentId()).isNull();
        // nenhum otype candidato mapeado
        assertThat(rules).noneMatch(r -> r.sourceType().endsWith("?") && !r.sourceType().equals("?"));
    }

    @Test
    void realMessierClassificationWithDatabaseRules() {
        List<TypeMappingRule> rules = simbadRules();
        CategoryRow other = categories.findByCode("other").orElseThrow();
        Map<String, String> byMessier = new HashMap<>();
        TreeMap<String, Integer> histogram = new TreeMap<>();
        for (TapRow row : TapJsonParser.parse(Fixtures.read("simbad/messier_types.json")).rows()) {
            String code = resolver.resolve("SIMBAD", row.text("otype").orElseThrow(),
                    row.text("morph_type").orElse(null), rules, other).categoryCode();
            byMessier.put(row.text("messier_id").orElseThrow(), code);
            histogram.merge(code, 1, Integer::sum);
        }
        assertThat(byMessier.get("M  31")).isEqualTo("galaxy_spiral");     // AGN + SA(s)b
        assertThat(byMessier.get("M  87")).isEqualTo("galaxy_elliptical"); // AGN + E-E/S0
        assertThat(byMessier.get("M  85")).isEqualTo("galaxy_lenticular"); // GiP + S0
        assertThat(byMessier.get("M  81")).isEqualTo("galaxy_spiral");     // Sy2 + SA (regra sem estagio)
        assertThat(byMessier.get("M 104")).isEqualTo("agn");               // LIN + morph '1' (fora do padrao)
        assertThat(byMessier.get("M   1")).isEqualTo("nebula_snr");
        assertThat(byMessier.get("M  42")).isEqualTo("nebula_emission");
        assertThat(byMessier.get("M  57")).isEqualTo("nebula_planetary");
        assertThat(byMessier.get("M  13")).isEqualTo("cluster_globular");
        assertThat(byMessier.get("M  45")).isEqualTo("cluster_open");
        assertThat(byMessier.get("M  40")).isEqualTo("other");
        assertThat(byMessier.get("M  73")).isEqualTo("other");
        assertThat(histogram.get("other")).isEqualTo(2);
    }
}
