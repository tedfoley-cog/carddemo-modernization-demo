package com.carddemo.batch.parity;

import com.carddemo.batch.support.SequentialDataset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** RETURN-CODES.txt: one 80-byte record per program step, {@code <JOB>.<STEP>.<PROGRAM>} then RC or ABEND. */
public final class ReturnCodeLog {

    private final List<String> entries = new ArrayList<>();

    public void returnCode(String step, int rc) {
        entries.add(pad(step) + "RC=%04d".formatted(rc));
    }

    public void abend(String step, String abendCode) {
        entries.add(pad(step) + "ABEND=" + abendCode);
    }

    public List<String> entries() {
        return List.copyOf(entries);
    }

    public void write(Path file) {
        try (SequentialDataset ds = SequentialDataset.openOutput(file, 80)) {
            entries.forEach(ds::write);
        }
    }

    private static String pad(String step) {
        return step.length() >= 40 ? step : step + " ".repeat(40 - step.length());
    }
}
