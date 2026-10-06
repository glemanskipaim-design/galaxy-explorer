package dev.galaxyexplorer.ingestion.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class IngestionPipelineTest {

    @Test
    void truncateKeepsShortTextAndCutsLongTextTo4000() {
        assertThat(IngestionPipeline.truncate("curto")).isEqualTo("curto");
        String exact = "a".repeat(IngestionPipeline.MAX_ERROR_SUMMARY);
        assertThat(IngestionPipeline.truncate(exact)).isEqualTo(exact);
        String cut = IngestionPipeline.truncate("b".repeat(IngestionPipeline.MAX_ERROR_SUMMARY + 1));
        assertThat(cut).hasSize(IngestionPipeline.MAX_ERROR_SUMMARY).endsWith("...");
    }

    @Test
    void partitionPreservesOrderAndLastPartialBatch() {
        assertThat(IngestionPipeline.partition(List.of(1, 2, 3, 4, 5), 2))
                .containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
    }
}
