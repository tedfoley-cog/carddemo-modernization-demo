package com.carddemo.batch.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.parity.ReturnCodeLog;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SequentialDatasetTest {

    @TempDir
    Path dir;

    @Test
    void writesFixedLengthRecordsWithoutLineTerminators() throws Exception {
        Path file = dir.resolve("OUT.PS");
        try (SequentialDataset ds = SequentialDataset.openOutput(file, 5)) {
            ds.write("AB");
            ds.write("CDEFGHIJ");
            assertThat(ds.count()).isEqualTo(2);
            assertThat(ds.path()).isEqualTo(file);
        }
        assertThat(Files.readString(file)).isEqualTo("AB   CDEFG");
    }

    @Test
    void unwritableLocationFails() throws Exception {
        Path blocker = Files.writeString(dir.resolve("file"), "x");
        assertThatThrownBy(() -> SequentialDataset.openOutput(blocker.resolve("child/OUT.PS"), 5))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void returnCodesAreEightyByteRecords() throws Exception {
        ReturnCodeLog log = new ReturnCodeLog();
        log.returnCode("POSTTRAN.STEP15.CBTRN02C", 4);
        log.abend("INTCALC.STEP15.CBACT04C", new LegacyAbendException(999, "x").formattedCode());
        log.write(dir.resolve("RC.txt"));
        String text = Files.readString(dir.resolve("RC.txt"));
        assertThat(text).hasSize(160);
        assertThat(text.substring(80)).startsWith("INTCALC.STEP15.CBACT04C                 ABEND=U0999");
        assertThat(log.entries()).hasSize(2);
    }

    @Test
    void abendCarriesUserCode() {
        LegacyAbendException abend = new LegacyAbendException(0, "orphan");
        assertThat(abend.abendCode()).isZero();
        assertThat(abend.formattedCode()).isEqualTo("U0000");
        assertThat(abend).hasMessage("orphan");
    }
}
