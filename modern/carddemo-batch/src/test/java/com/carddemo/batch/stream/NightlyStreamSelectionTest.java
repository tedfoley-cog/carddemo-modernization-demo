package com.carddemo.batch.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class NightlyStreamSelectionTest {

    @Test
    void nightlyStreamRunsEveryJobInJclOrder() {
        assertThat(NightlyStream.select("nightlyStream"))
                .containsExactly(NightlyStream.POSTTRAN, NightlyStream.INTCALC, NightlyStream.COMBTRAN,
                        NightlyStream.TRANREPT, NightlyStream.CREASTMT);
        assertThat(NightlyStream.select(null)).hasSize(5);
        assertThat(NightlyStream.select(" ")).hasSize(5);
    }

    @Test
    void subsetIsAlwaysExecutedInStreamOrder() {
        assertThat(NightlyStream.select("TRANREPT, postTransactionsJob,INTCALC"))
                .containsExactly(NightlyStream.POSTTRAN, NightlyStream.INTCALC, NightlyStream.TRANREPT);
    }

    @Test
    void unknownJobIsRefused() {
        assertThatThrownBy(() -> NightlyStream.select("POSTTRAN,FOO")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FOO");
    }

    @Test
    void utilityJobHasNoReturnCodeStep() {
        assertThat(NightlyStream.COMBTRAN.programStep()).isNull();
        assertThat(NightlyStream.CREASTMT.programStep()).isEqualTo("CREASTMT.STEP040.CBSTM03A");
    }

    @Test
    void commandLineExitCodeFollowsStreamResult() throws Exception {
        NightlyStreamRunner runner = mock(NightlyStreamRunner.class);
        when(runner.run()).thenReturn(new StreamResult(List.of(), false));
        StreamCommandLineRunner cli = new StreamCommandLineRunner(runner);
        cli.run();
        assertThat(cli.getExitCode()).isEqualTo(16);
        when(runner.run()).thenReturn(new StreamResult(List.of(), true));
        cli.run();
        assertThat(cli.getExitCode()).isZero();
    }
}
