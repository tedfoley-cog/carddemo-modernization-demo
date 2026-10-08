package com.carddemo.domain.copybook;

import com.carddemo.domain.fixedwidth.RecordLayout;

/** Maps a domain object to and from its legacy copybook record. Boundary use only. */
public interface RecordCodec<T> {

    RecordLayout layout();

    T decode(String record);

    String encode(T value);
}
