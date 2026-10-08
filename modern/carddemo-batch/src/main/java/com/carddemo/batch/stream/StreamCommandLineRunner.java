package com.carddemo.batch.stream;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Entry point for {@code java -jar carddemo-batch.jar --seed-dir=... --out=...} and Cloud Run Jobs. */
@Component
@Profile("!test")
public class StreamCommandLineRunner implements CommandLineRunner, ExitCodeGenerator {

    private final NightlyStreamRunner runner;
    private int exitCode;

    public StreamCommandLineRunner(NightlyStreamRunner runner) {
        this.runner = runner;
    }

    @Override
    public void run(String... args) throws Exception {
        exitCode = runner.run().exitCode();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
