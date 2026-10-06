package dev.galaxyexplorer.ingestion.connector.tap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class AdqlLiteralsTest {

    @Test
    void doublesSingleQuotes() {
        assertThat(AdqlLiterals.quote("Barnard's Star")).isEqualTo("'Barnard''s Star'");
        assertThat(AdqlLiterals.quote("x' OR '1'='1")).isEqualTo("'x'' OR ''1''=''1'");
    }

    @Test
    void rejectsControlCharacters() {
        assertThatThrownBy(() -> AdqlLiterals.quote("M31\n; DROP")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdqlLiterals.quote("a\u0000b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdqlLiterals.quote(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lists() {
        assertThat(AdqlLiterals.inList(List.of("basic", "ident"))).isEqualTo("('basic', 'ident')");
        assertThat(AdqlLiterals.inListNumeric(List.of(3L, 1L))).isEqualTo("(3, 1)");
        assertThatThrownBy(() -> AdqlLiterals.inListNumeric(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
