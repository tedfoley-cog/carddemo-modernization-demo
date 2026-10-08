package com.carddemo.batch.stream;

import com.carddemo.batch.config.CardDemoBatchProperties;
import com.carddemo.batch.job.StreamContext;
import com.carddemo.batch.parity.ParityExporter;
import com.carddemo.batch.parity.ReturnCodeLog;
import com.carddemo.batch.seed.SeedLoader;
import com.carddemo.batch.support.LegacyAbendException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Component;

/**
 * Runs the nightly stream in JCL order (POSTTRAN, INTCALC, COMBTRAN, TRANREPT, CREASTMT), stops at
 * the first job that abends or fails, then writes RETURN-CODES.txt and unloads the master tables.
 */
@Component
public class NightlyStreamRunner {

    private static final Logger log = LoggerFactory.getLogger(NightlyStreamRunner.class);

    private final JobLauncher jobLauncher;
    private final Map<String, Job> jobs;
    private final SeedLoader seedLoader;
    private final ParityExporter exporter;
    private final CardDemoBatchProperties properties;

    public NightlyStreamRunner(JobLauncher jobLauncher, Map<String, Job> jobs, SeedLoader seedLoader,
                               ParityExporter exporter, CardDemoBatchProperties properties) {
        this.jobLauncher = jobLauncher;
        this.jobs = jobs;
        this.seedLoader = seedLoader;
        this.exporter = exporter;
        this.properties = properties;
    }

    public StreamResult run() throws Exception {
        Path out = properties.outDir();
        Files.createDirectories(out);
        if (properties.loadSeeds()) {
            seedLoader.load();
        }
        ReturnCodeLog codes = new ReturnCodeLog();
        List<StreamResult.JobResult> results = new ArrayList<>();
        boolean completed = true;
        for (NightlyStream job : NightlyStream.select(properties.jobName())) {
            long started = System.nanoTime();
            JobExecution execution = jobLauncher.run(jobs.get(job.springJob()), parameters(job));
            long millis = (System.nanoTime() - started) / 1_000_000;
            long records = execution.getExecutionContext().getLong(StreamContext.RECORDS, 0);
            String status;
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                int rc = execution.getExecutionContext().getInt(StreamContext.RETURN_CODE, 0);
                status = "RC=%04d".formatted(rc);
                if (job.programStep() != null) {
                    codes.returnCode(job.programStep(), rc);
                }
            } else {
                LegacyAbendException abend = findAbend(execution);
                status = abend != null ? "ABEND=" + abend.formattedCode() : "FAILED";
                if (job.programStep() != null) {
                    if (abend != null) {
                        codes.abend(job.programStep(), abend.formattedCode());
                    } else {
                        codes.returnCode(job.programStep(), 12);
                    }
                }
                completed = false;
            }
            log.info("JOB {} ({}) {} records={} elapsedMs={}", job, job.springJob(), status, records, millis);
            results.add(new StreamResult.JobResult(job, status, records, millis));
            if (!completed) {
                log.warn("STREAM ABENDED in {} - remaining jobs not run", job);
                break;
            }
        }
        if (properties.exportParityArtifacts()) {
            exporter.exportMasters(out);
            codes.write(out.resolve("RETURN-CODES.txt"));
        }
        return new StreamResult(results, completed);
    }

    private JobParameters parameters(NightlyStream job) {
        return new JobParametersBuilder()
                .addString("job", job.name())
                .addLocalDateTime("businessTimestamp", properties.businessTimestamp())
                .addLong("run", System.currentTimeMillis())
                .toJobParameters();
    }

    private static LegacyAbendException findAbend(JobExecution execution) {
        for (Throwable t : execution.getAllFailureExceptions()) {
            for (Throwable c = t; c != null; c = c.getCause()) {
                if (c instanceof LegacyAbendException abend) {
                    log.error("Abend {}: {}", abend.formattedCode(), abend.getMessage());
                    return abend;
                }
            }
            log.error("Job failed", t);
        }
        return null;
    }
}
