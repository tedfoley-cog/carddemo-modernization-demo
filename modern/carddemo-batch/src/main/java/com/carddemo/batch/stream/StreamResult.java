package com.carddemo.batch.stream;

import java.util.List;

/** Outcome of a stream run: per-job results and the process exit code. */
public record StreamResult(List<JobResult> jobs, boolean completed) {

    public record JobResult(NightlyStream job, String status, long records, long millis) {
    }

    /** 0 when every job ended with RC 0 or 4, 16 when the stream stopped on an abend or failure. */
    public int exitCode() {
        return completed ? 0 : 16;
    }
}
