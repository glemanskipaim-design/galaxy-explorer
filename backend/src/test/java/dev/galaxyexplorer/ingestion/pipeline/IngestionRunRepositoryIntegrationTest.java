package dev.galaxyexplorer.ingestion.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.catalog.source.SourceRepository;

class IngestionRunRepositoryIntegrationTest extends IntegrationTestBase {

    @Autowired
    IngestionRunRepository runs;
    @Autowired
    SourceRepository sources;

    @Test
    void latestStatusIsEmptyWhenTheSourceNeverRanWithThatTrigger() {
        assertThat(runs.latestStatus(SourceCode.ESA_HUBBLE, RunTrigger.MANUAL)).isEmpty();
    }

    @Test
    void latestStatusReturnsTheMostRecentRunOfThatSourceAndTrigger() {
        short hubble = sources.getByCode(SourceCode.ESA_HUBBLE).id();
        short webb = sources.getByCode(SourceCode.ESA_WEBB).id();
        runs.close(runs.open(hubble, RunTrigger.MANUAL, "test"), RunStatus.OK, 1, 0, null);
        runs.close(runs.open(hubble, RunTrigger.MANUAL, "test"), RunStatus.PARTIAL, 1, 1, "falhou 1");
        // outro gatilho e outra fonte nao interferem
        runs.close(runs.open(hubble, RunTrigger.ON_DEMAND, "test"), RunStatus.OK, 1, 0, null);
        runs.close(runs.open(webb, RunTrigger.MANUAL, "test"), RunStatus.OK, 1, 0, null);

        assertThat(runs.latestStatus(SourceCode.ESA_HUBBLE, RunTrigger.MANUAL)).contains(RunStatus.PARTIAL);
        assertThat(runs.latestStatus(SourceCode.ESA_WEBB, RunTrigger.MANUAL)).contains(RunStatus.OK);
    }
}
