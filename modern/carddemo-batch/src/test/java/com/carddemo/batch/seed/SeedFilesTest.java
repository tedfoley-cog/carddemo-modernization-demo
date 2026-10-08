package com.carddemo.batch.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.TestProperties;
import com.carddemo.batch.config.CardDemoBatchProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeedFilesTest {

    @TempDir
    Path seedDir;

    @Test
    void readsFileFromSeedDirectory() throws Exception {
        Files.writeString(seedDir.resolve("trantype.txt"), "01Purchase\r\n\r\n");
        assertThat(seedFiles(false).records("trantype.txt", 60)).singleElement()
                .satisfies(r -> assertThat(r).hasSize(60).startsWith("01Purchase"));
    }

    @Test
    void missingFileFailsByDefault() {
        assertThatThrownBy(() -> seedFiles(false).records("dailytran.txt", 350))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dailytran.txt")
                .hasMessageContaining("CARDDEMO_SEED_DEFAULTS");
    }

    @Test
    void missingFileFallsBackToBundledExtractInScenarioMode() {
        assertThat(seedFiles(true).records("trantype.txt", 60)).isNotEmpty();
    }

    private SeedFiles seedFiles(boolean seedDefaults) {
        CardDemoBatchProperties p = TestProperties.legacy();
        return new SeedFiles(new CardDemoBatchProperties(p.jobName(), p.businessTimestamp(), p.interestRunId(),
                p.reportStartDate(), p.reportEndDate(), seedDir, p.outDir(), true, seedDefaults, true, p.legacyFixes()));
    }
}
