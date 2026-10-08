package com.carddemo.batch.support;

import com.carddemo.domain.fixedwidth.FixedWidth;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A RECFM=F sequential dataset opened for OUTPUT: records are padded/truncated to the LRECL and
 * written back to back without line terminators, as on the mainframe.
 */
public final class SequentialDataset implements RecordSink, AutoCloseable {

    private final Path path;
    private final int lrecl;
    private final BufferedWriter writer;
    private long count;

    private SequentialDataset(Path path, int lrecl) throws IOException {
        this.path = path;
        this.lrecl = lrecl;
        Files.createDirectories(path.toAbsolutePath().getParent());
        this.writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path), FixedWidth.CHARSET), 1 << 16);
    }

    public static SequentialDataset openOutput(Path path, int lrecl) {
        try {
            return new SequentialDataset(path, lrecl);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open " + path, e);
        }
    }

    @Override
    public void write(String record) {
        try {
            writer.write(FixedWidth.padRight(record, lrecl));
            count++;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + path, e);
        }
    }

    public long count() {
        return count;
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot close " + path, e);
        }
    }
}
