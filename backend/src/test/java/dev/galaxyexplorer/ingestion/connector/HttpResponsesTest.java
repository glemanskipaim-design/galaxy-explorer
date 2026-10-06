package dev.galaxyexplorer.ingestion.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;

import dev.galaxyexplorer.support.Fixtures;

class HttpResponsesTest {

    private static final String REAL_ERROR = Fixtures.read("simbad/error_bad_adql.xml");

    @Test
    void errorFixtureNeverCarriesTheUsersIp() {
        // Recapturar a fixture pelo README reintroduz o IP real: este teste falha se isso acontecer.
        Matcher m = Pattern.compile("<INFO\\s+name=\"USER\"\\s+value=\"([^\"]*)\"").matcher(REAL_ERROR);
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEqualTo("0.0.0.0 (anonymous)");
    }

    @Test
    void abbreviateDropsUserInfoElementEvenWhenItWouldFitInTheCut() {
        String abbreviated = HttpResponses.abbreviate(REAL_ERROR);
        assertThat(abbreviated).doesNotContain("USER").doesNotContain("0.0.0.0")
                .contains("Incorrect ADQL query").doesNotContain("\n");
        String shortBody = "<INFO name=\"USER\" value=\"203.0.113.7 (anonymous)\"/> erro";
        assertThat(HttpResponses.abbreviate(shortBody)).isEqualTo("erro");
    }

    @Test
    void abbreviateCutsAt500() {
        assertThat(HttpResponses.abbreviate("y".repeat(900))).hasSize(HttpResponses.MAX_ERROR_BODY_CHARS + 3);
        assertThat(HttpResponses.abbreviate(null)).isEmpty();
    }

    @Test
    void singleStatusRule() {
        assertThatThrownBy(() -> HttpResponses.throwIfFailed("X", HttpStatusCode.valueOf(429), ""))
                .isInstanceOf(SourceUnavailableException.class);
        assertThatThrownBy(() -> HttpResponses.throwIfFailed("X", HttpStatusCode.valueOf(503), ""))
                .isInstanceOf(SourceUnavailableException.class);
        assertThatThrownBy(() -> HttpResponses.throwIfFailed("X", HttpStatusCode.valueOf(400), REAL_ERROR))
                .isInstanceOf(SourceQueryException.class).hasMessageContaining("HTTP 400")
                .hasMessageNotContaining("USER");
        assertThatThrownBy(() -> HttpResponses.throwIfFailed("X", HttpStatusCode.valueOf(302), ""))
                .isInstanceOf(SourceQueryException.class);
        assertThatCode(() -> HttpResponses.throwIfFailed("X", HttpStatusCode.valueOf(200), "")).doesNotThrowAnyException();
    }
}
