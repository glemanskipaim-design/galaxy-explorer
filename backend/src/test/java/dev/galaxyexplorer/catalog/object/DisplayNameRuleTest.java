package dev.galaxyexplorer.catalog.object;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Os casos de M45, Betelgeuse, M31 e Sgr A* usam identificadores copiados da
 * fixture real {@code sample_ident.json}; os demais testam a regra em si.
 */
class DisplayNameRuleTest {

    @Test
    void messierWinsOverNameAndMainId() {
        // M45: main_id 'Cl Melotte   22'
        assertThat(DisplayNameRule.choose("Cl Melotte   22",
                List.of("C 0344+239", "Cl Melotte   22", "M  45", "NAME Pleiades", "NAME Seven Sisters")))
                .isEqualTo("M 45");
    }

    @Test
    void shortestNameWithoutPrefixWhenNoMessier() {
        assertThat(DisplayNameRule.choose("* alf Ori", List.of("* alf Ori", "NAME Betelgeuse", "HD  39801")))
                .isEqualTo("Betelgeuse");
        assertThat(DisplayNameRule.choose("x",
                List.of("NAME And Nebula", "NAME Andromeda", "NAME Andromeda Galaxy", "NAME Andromeda Nebula")))
                .isEqualTo("Andromeda");
    }

    @Test
    void mainIdThatIsANameWinsOverShorterNames() {
        // Sgr A*: o NAME mais curto ('Sgr A') e outro objeto astrofisico; o main_id e o nome certo.
        assertThat(DisplayNameRule.choose("NAME Sgr A*", List.of("NAME Sagittarius A", "NAME Sagittarius A*",
                "NAME Sgr A", "NAME Sgr A *", "NAME Sgr A*", "NAME Sgr A* IR"))).isEqualTo("Sgr A*");
    }

    @Test
    void mainIdWhenNothingElse() {
        assertThat(DisplayNameRule.choose("HD  39801", List.of("HD  39801", "HIP 27989"))).isEqualTo("HD 39801");
    }

    @Test
    void minkowskiIsNotMessier() {
        assertThat(DisplayNameRule.choose("PN M 1-67", List.of("M 1-67", "PN M 1-67"))).isEqualTo("PN M 1-67");
    }
}
