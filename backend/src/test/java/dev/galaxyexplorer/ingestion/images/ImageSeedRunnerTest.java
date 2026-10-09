package dev.galaxyexplorer.ingestion.images;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.ingestion.pipeline.RunStatus;

class ImageSeedRunnerTest {

    @Test
    void exitCodeByDefaultFailsOnlyForFailedRuns() {
        assertThat(ImageSeedRunner.exitCode(true, true, false)).isEqualTo(1);
        assertThat(ImageSeedRunner.exitCode(false, true, false)).isZero();
        assertThat(ImageSeedRunner.exitCode(false, false, false)).isZero();
    }

    @Test
    void exitCodeInStrictModeFailsOnAnyFailedObjectSoTheSeedRetries() {
        assertThat(ImageSeedRunner.exitCode(false, true, true)).isEqualTo(1);
        assertThat(ImageSeedRunner.exitCode(true, false, true)).isEqualTo(1);
        assertThat(ImageSeedRunner.exitCode(false, false, true)).isZero();
    }

    @Test
    void completeOnlyWhenEveryRequestedSourceHasLatestRunOk() {
        assertThat(ImageSeedRunner.alreadyComplete(List.of(Optional.of(RunStatus.OK), Optional.of(RunStatus.OK))))
                .isTrue();
        assertThat(ImageSeedRunner.alreadyComplete(List.of(Optional.of(RunStatus.OK), Optional.of(RunStatus.PARTIAL))))
                .isFalse();
        assertThat(ImageSeedRunner.alreadyComplete(List.of(Optional.of(RunStatus.OK), Optional.of(RunStatus.RUNNING))))
                .isFalse();
        // banco novo (ex. depois de "docker compose down -v"): nenhum run = refaz a carga
        assertThat(ImageSeedRunner.alreadyComplete(List.of(Optional.empty(), Optional.empty()))).isFalse();
        assertThat(ImageSeedRunner.alreadyComplete(List.of())).isFalse();
    }

    @Test
    void eachImageSourceMapsToItsDataSource() {
        assertThat(ImageSeedRunner.sourceCode(ImageSource.CUTOUT)).isEqualTo(SourceCode.CDS_HIPS2FITS);
        assertThat(ImageSeedRunner.sourceCode(ImageSource.ESA_HUBBLE)).isEqualTo(SourceCode.ESA_HUBBLE);
        assertThat(ImageSeedRunner.sourceCode(ImageSource.ESA_WEBB)).isEqualTo(SourceCode.ESA_WEBB);
    }
}
