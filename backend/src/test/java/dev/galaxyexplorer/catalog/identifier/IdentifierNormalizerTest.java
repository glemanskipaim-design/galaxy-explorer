package dev.galaxyexplorer.catalog.identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifierNormalizerTest {

    @ParameterizedTest
    @ValueSource(strings = {"M  31", "M31", "m 31", " M 31 ", "M   31"})
    void spacingAndCaseCollapseToSameKey(String raw) {
        assertThat(IdentifierNormalizer.normalize(raw)).isEqualTo("m31");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "NAME Betelgeuse|betelgeuse",
            "* alf Ori|alfori",
            "V* alf Ori|alfori",
            "NAME Sgr A*|sgra*",
            "Andrômeda|andromeda",
            "NGC   224|ngc224"})
    void removesSimbadPrefixesAndDiacritics(String raw, String expected) {
        assertThat(IdentifierNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @Test
    void doesNotStripLeadingZeros() {
        assertThat(IdentifierNormalizer.normalize("M031")).isEqualTo("m031");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void nullOrBlankThrows(String raw) {
        assertThatThrownBy(() -> IdentifierNormalizer.normalize(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "M  31|M",
            "NAME Betelgeuse|NAME",
            "* alf Ori|*",
            "NGC   224|NGC"})
    void catalogIsFirstToken(String identifier, String catalog) {
        assertThat(IdentifierNormalizer.catalogOf(identifier)).isEqualTo(catalog);
    }

    @Test
    void catalogIsNullWithoutSpace() {
        assertThat(IdentifierNormalizer.catalogOf("Betelgeuse")).isNull();
    }
}
