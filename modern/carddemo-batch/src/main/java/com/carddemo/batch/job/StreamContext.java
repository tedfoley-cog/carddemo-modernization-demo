package com.carddemo.batch.job;

/** Job execution-context keys shared between the jobs and the stream runner. */
public final class StreamContext {

    public static final String RETURN_CODE = "returnCode";
    public static final String RECORDS = "records";
    public static final String DALYREJS = "DALYREJS.PS";
    public static final String SYSTRAN = "SYSTRAN.PS";
    public static final String TRANREPT = "TRANREPT.RPT";
    public static final String STATEMNT = "STATEMNT.PS";
    public static final String STATEMNT_HTML = "STATEMNT.HTML";

    private StreamContext() {
    }
}
