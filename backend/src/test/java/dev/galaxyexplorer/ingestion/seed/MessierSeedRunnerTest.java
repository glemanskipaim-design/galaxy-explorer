package dev.galaxyexplorer.ingestion.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.ingestion.pipeline.RunStatus;

class MessierSeedRunnerTest {

    @Test
    void defaultsToMessierWhenNothingConfigured() {
        assertThat(MessierSeedRunner.queriesToRun(List.of())).isEqualTo(MessierDesignations.all());
        assertThat(MessierSeedRunner.queriesToRun(null)).hasSize(110);
        assertThat(MessierSeedRunner.queriesToRun(List.of(" ", ""))).hasSize(110);
    }

    @Test
    void explicitQueriesReplaceMessierList() {
        assertThat(MessierSeedRunner.queriesToRun(List.of(" M51 ", "Vega"))).containsExactly("M51", "Vega");
    }

    @Test
    void onlyMissingKeepsQueriesAbsentLocallyInOrder() {
        Set<String> present = Set.of("M1", "M3");
        assertThat(MessierSeedRunner.missingOnly(List.of("M1", "M2", "M3", "M4"), present::contains))
                .containsExactly("M2", "M4");
    }

    @Test
    void onlyMissingIsEmptyWhenEverythingIsPresent() {
        assertThat(MessierSeedRunner.missingOnly(MessierDesignations.all(), q -> true)).isEmpty();
    }

    @Test
    void exitCodeIsOneOnlyForFailedRunsByDefault() {
        assertThat(MessierSeedRunner.exitCode(RunStatus.FAILED, 110, false)).isEqualTo(1);
        assertThat(MessierSeedRunner.exitCode(RunStatus.PARTIAL, 3, false)).isZero();
        assertThat(MessierSeedRunner.exitCode(RunStatus.OK, 0, false)).isZero();
    }

    @Test
    void exitCodeInOnlyMissingModeSignalsAnyFailedQuerySoTheCallerRetries() {
        assertThat(MessierSeedRunner.exitCode(RunStatus.PARTIAL, 3, true)).isEqualTo(1);
        assertThat(MessierSeedRunner.exitCode(RunStatus.FAILED, 1, true)).isEqualTo(1);
        assertThat(MessierSeedRunner.exitCode(RunStatus.OK, 0, true)).isZero();
    }
}
