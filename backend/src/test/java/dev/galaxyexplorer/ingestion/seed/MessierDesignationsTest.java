package dev.galaxyexplorer.ingestion.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class MessierDesignationsTest {

    @Test
    void oneHundredTenDistinctQueriesInOrder() {
        List<String> all = MessierDesignations.all();
        assertThat(all).hasSize(110);
        assertThat(all.get(0)).isEqualTo("M1");
        assertThat(all.get(109)).isEqualTo("M110");
        assertThat(new HashSet<>(all)).hasSize(110);
    }
}
