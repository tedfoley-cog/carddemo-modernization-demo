package com.carddemo.batch.stream;

import java.util.Arrays;
import java.util.List;

/** The nightly JCL jobs in scheduler order, with the program step each reports in RETURN-CODES.txt. */
public enum NightlyStream {
    POSTTRAN("postTransactionsJob", "POSTTRAN.STEP15.CBTRN02C"),
    INTCALC("interestCalculationJob", "INTCALC.STEP15.CBACT04C"),
    COMBTRAN("combineTransactionsJob", null),
    TRANREPT("transactionReportJob", "TRANREPT.STEP10R.CBTRN03C"),
    CREASTMT("statementJob", "CREASTMT.STEP040.CBSTM03A");

    public static final String STREAM_JOB_NAME = "nightlyStream";

    private final String springJob;
    private final String programStep;

    NightlyStream(String springJob, String programStep) {
        this.springJob = springJob;
        this.programStep = programStep;
    }

    public String springJob() {
        return springJob;
    }

    /** {@code <JOB>.<STEP>.<PROGRAM>}, or null for utility-only jobs (SORT/REPRO), which the harness does not list. */
    public String programStep() {
        return programStep;
    }

    /**
     * Jobs selected by a Cloud Run job name: {@code nightlyStream} runs all of them; otherwise a
     * comma-separated list of JCL or Spring job names, always executed in stream order.
     */
    public static List<NightlyStream> select(String jobName) {
        if (jobName == null || jobName.isBlank() || STREAM_JOB_NAME.equals(jobName)) {
            return List.of(values());
        }
        List<String> names = Arrays.stream(jobName.split(",")).map(String::trim).toList();
        List<NightlyStream> selected = Arrays.stream(values())
                .filter(j -> names.contains(j.name()) || names.contains(j.springJob))
                .toList();
        if (selected.size() != names.size()) {
            throw new IllegalArgumentException("Unknown job in '" + jobName + "'; expected nightlyStream or one of "
                    + Arrays.toString(values()));
        }
        return selected;
    }
}
