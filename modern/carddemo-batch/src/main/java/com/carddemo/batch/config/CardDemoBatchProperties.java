package com.carddemo.batch.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * All runtime configuration of the batch, bound from {@code carddemo.batch.*}. Every value can be
 * supplied as an environment variable (e.g. {@code CARDDEMO_BATCH_SEED_DIR}) so a Cloud Run Job
 * triggered by Cloud Scheduler needs no image rebuild; the CLI aliases {@code --seed-dir} and
 * {@code --out} map onto the same properties.
 */
@Validated
@ConfigurationProperties("carddemo.batch")
public record CardDemoBatchProperties(
        /** Job to run: nightlyStream or a single job name such as postTransactionsJob. */
        @NotBlank String jobName,
        /** Business date/time that replaces FUNCTION CURRENT-DATE. */
        @NotNull LocalDateTime businessTimestamp,
        /** INTCALC PARM: prefix of generated interest transaction ids. */
        @NotBlank String interestRunId,
        @NotNull LocalDate reportStartDate,
        @NotNull LocalDate reportEndDate,
        /** Directory with ASCII seed extracts (acctdata.txt, dailytran.txt, ...). */
        Path seedDir,
        /** Directory receiving the sequential datasets, reports and parity artifacts. */
        @NotNull Path outDir,
        /** Load seed files into the database before running. */
        boolean loadSeeds,
        /** Write the KSDS unload artifacts (ACCTDATA.dat, TCATBALF.dat, TRANSACT.dat) after the run. */
        boolean exportParityArtifacts,
        @NotNull LegacyFixes legacyFixes) {

    /**
     * Opt-in corrections for documented legacy defects (see LEGACY-DEFECTS.md). All default to
     * false so the stream stays byte-for-byte equivalent to the COBOL.
     */
    public record LegacyFixes(
            boolean keepOverlimitReason,
            boolean limitCheckUsesCurrentBalance,
            boolean skipMissingDisclosureGroup,
            boolean unboundedStatementTable,
            boolean skipOrphanXref,
            boolean reportNoDoubleCountLastAmount) {
    }
}
