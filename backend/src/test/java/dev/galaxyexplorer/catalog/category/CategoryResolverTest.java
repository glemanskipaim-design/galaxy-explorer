package dev.galaxyexplorer.catalog.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Regras construidas no teste: verifica a mecanica de decisao, nao afirma nada sobre o ceu. */
class CategoryResolverTest {

    private static final CategoryRow OTHER = new CategoryRow(99, "other", null, "Outro", "Other");

    private final CategoryResolver resolver = new CategoryResolver();

    private static TypeMappingRule rule(int id, String type, String pattern, String category, int priority) {
        return new TypeMappingRule(id, type, pattern, id * 10, category, (short) priority);
    }

    private final List<TypeMappingRule> rules = List.of(
            rule(1, "AGN", "", "agn", 0),
            rule(2, "AGN", "^S(A|B|AB)?(\\([^)]*\\))?[abcdm]", "galaxy_spiral", 20),
            rule(3, "AGN", "^S(A|B|AB)?(\\([^)]*\\))?0", "galaxy_lenticular", 30),
            rule(4, "AGN", "^S", "galaxy_spiral_generic", 30),
            rule(5, "X", "", "x_ray_source", 0));

    @Test
    void morphologyRuleBeatsGenericByPriority() {
        CategoryDecision d = resolver.resolve("SIMBAD", "AGN", "SA(s)b", rules.subList(0, 3), OTHER);
        assertThat(d.categoryCode()).isEqualTo("galaxy_spiral");
        assertThat(d.basis()).contains("otype=AGN").contains("morph_type=SA(s)b").contains("galaxy_spiral");
    }

    @Test
    void tieOnPriorityGoesToLowestId() {
        CategoryDecision d = resolver.resolve("SIMBAD", "AGN", "S0", rules, OTHER);
        assertThat(d.categoryCode()).isEqualTo("galaxy_lenticular"); // id 3 < id 4, ambos prioridade 30
    }

    @Test
    void nullMorphologyOnlyMatchesEmptyPattern() {
        assertThat(resolver.resolve("SIMBAD", "AGN", null, rules, OTHER).categoryCode()).isEqualTo("agn");
        assertThat(resolver.resolve("SIMBAD", "AGN", "   ", rules, OTHER).categoryCode()).isEqualTo("agn");
    }

    @Test
    void unknownTypeFallsBackToOtherWithExplanation() {
        CategoryDecision d = resolver.resolve("SIMBAD", "err", null, rules, OTHER);
        assertThat(d.categoryCode()).isEqualTo("other");
        assertThat(d.categoryId()).isEqualTo(99);
        assertThat(d.basis()).isEqualTo("SIMBAD otype=err sem regra de mapeamento");
    }

    @Test
    void invalidRegexIsIgnoredNotFatal() {
        List<TypeMappingRule> withBroken = List.of(rule(1, "AGN", "", "agn", 0), rule(2, "AGN", "([", "broken", 50));
        assertThat(resolver.resolve("SIMBAD", "AGN", "SA(s)b", withBroken, OTHER).categoryCode()).isEqualTo("agn");
    }

    @Test
    void typeMatchIsExact() {
        assertThat(resolver.resolve("SIMBAD", "agn", "SA(s)b", rules, OTHER).categoryCode()).isEqualTo("other");
    }
}
