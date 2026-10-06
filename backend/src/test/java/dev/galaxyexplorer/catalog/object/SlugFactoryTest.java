package dev.galaxyexplorer.catalog.object;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugFactoryTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "M 31|messier-31",
            "M  1|messier-1",
            "M 110|messier-110",
            "Betelgeuse|betelgeuse",
            "Sgr A*|sgr-a",
            "Nebulosa de Órion|nebulosa-de-orion",
            "Cl Melotte 22|cl-melotte-22"})
    void baseSlug(String displayName, String slug) {
        assertThat(SlugFactory.baseSlug(displayName)).isEqualTo(slug);
    }

    @Test
    void onlySymbolsFallsBackToObject() {
        assertThat(SlugFactory.baseSlug("***")).isEqualTo("object");
    }

    @Test
    void suffixOnCollision() {
        assertThat(SlugFactory.withSuffix("messier-31", 2)).isEqualTo("messier-31-2");
    }

    @Test
    void respectsMaxLength() {
        String longName = "a".repeat(300);
        assertThat(SlugFactory.baseSlug(longName)).hasSize(SlugFactory.MAX_LENGTH);
        assertThat(SlugFactory.withSuffix(SlugFactory.baseSlug(longName), 12)).hasSizeLessThanOrEqualTo(
                SlugFactory.MAX_LENGTH).endsWith("-12");
    }
}
