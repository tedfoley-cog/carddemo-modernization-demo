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
 * Resolves the ASCII seed files (app/data/ASCII layout). A file present in the configured seed
 * directory overrides the default bundled with the application, file by file, like the legacy
 * harness's {@code --data-dir}.
 */
@Component
public class SeedFiles {

    static final String DEFAULTS = "seed-defaults/";

    private final Path seedDir;

    public SeedFiles(CardDemoBatchProperties properties) {
        this.seedDir = properties.seedDir();
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
            try (InputStream in = new ClassPathResource(DEFAULTS + fileName).getInputStream()) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read seed file " + fileName, e);
        }
    }
}
