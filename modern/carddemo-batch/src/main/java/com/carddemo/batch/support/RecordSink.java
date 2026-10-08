package com.carddemo.batch.support;

/** Destination for fixed-length records of a sequential (RECFM=F) dataset. */
@FunctionalInterface
public interface RecordSink {

    void write(String record);
}
