package com.carddemo.batch.seed;

import com.carddemo.batch.config.CardDemoBatchProperties;
import com.carddemo.domain.fixedwidth.FixedWidth;
import com.carddemo.domain.fixedwidth.FixedWidthException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Resolves the ASCII seed files (app/data/ASCII layout) from the configured seed directory. A missing
 * file is an error unless {@code seed-defaults} is on (parity scenarios), in which case the extract
 * bundled with the application is used, file by file, like the legacy harness's {@code --data-dir}.
 */
@Component
public class SeedFiles {

    static final String DEFAULTS = "seed-defaults/";

    private final Path seedDir;
    private final boolean seedDefaults;

    public SeedFiles(CardDemoBatchProperties properties) {
        this.seedDir = properties.seedDir();
        this.seedDefaults = properties.seedDefaults();
    }

    /** Records of a seed file, CR stripped, blank lines skipped, space padded to the LRECL. */
    public List<String> records(String fileName, int lrecl) {
        String text = new String(read(fileName), FixedWidth.CHARSET);
        List<String> records = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String record = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (record.isBlank()) {
                continue;
            }
            if (record.length() > lrecl) {
                throw new FixedWidthException(fileName + ": record longer than LRECL " + lrecl + ": " + record.length());
            }
            records.add(FixedWidth.padRight(record, lrecl));
        }
        return records;
    }

    private byte[] read(String fileName) {
        try {
            if (seedDir != null && !seedDir.toString().isEmpty() && Files.isRegularFile(seedDir.resolve(fileName))) {
                return Files.readAllBytes(seedDir.resolve(fileName));
            }
            if (!seedDefaults) {
                throw new IllegalStateException("Seed file " + fileName + " not found in seed directory '"
                        + seedDir + "' (set CARDDEMO_SEED_DEFAULTS=true to fall back to the bundled extracts)");
            }
            try (InputStream in = new ClassPathResource(DEFAULTS + fileName).getInputStream()) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read seed file " + fileName, e);
        }
    }
}
