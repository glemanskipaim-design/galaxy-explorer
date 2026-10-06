package dev.galaxyexplorer.ingestion.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

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
}
