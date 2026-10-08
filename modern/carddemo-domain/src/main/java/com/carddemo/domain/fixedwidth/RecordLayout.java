package com.carddemo.domain.fixedwidth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Geometry of one copybook record (01 level): ordered elementary items and the LRECL.
 * Built once per copybook and shared; thread-safe after construction.
 */
public final class RecordLayout {

    private final String copybook;
    private final int length;
    private final Map<String, FieldSpec> fields;

    private RecordLayout(String copybook, int length, Map<String, FieldSpec> fields) {
        this.copybook = copybook;
        this.length = length;
        this.fields = Collections.unmodifiableMap(fields);
    }

    public static Builder builder(String copybook) {
        return new Builder(copybook);
    }

    public String copybook() {
        return copybook;
    }

    public int length() {
        return length;
    }

    public List<FieldSpec> fields() {
        return new ArrayList<>(fields.values());
    }

    public FieldSpec field(String name) {
        FieldSpec spec = fields.get(name);
        if (spec == null) {
            throw new FixedWidthException(copybook + " has no field " + name);
        }
        return spec;
    }

    public RecordReader reader(String record) {
        if (record.length() != length) {
            throw new FixedWidthException(copybook + ": expected " + length + " bytes, got " + record.length());
        }
        return new RecordReader(this, record);
    }

    public RecordWriter writer() {
        return new RecordWriter(this);
    }

    public static final class Builder {
        private final String copybook;
        private final Map<String, FieldSpec> fields = new LinkedHashMap<>();
        private int offset;
        private int fillers;

        private Builder(String copybook) {
            this.copybook = copybook;
        }

        public Builder text(String name, int length) {
            return add(name, length, FieldType.ALPHANUMERIC, 0);
        }

        public Builder unsigned(String name, int length) {
            return add(name, length, FieldType.UNSIGNED, 0);
        }

        public Builder signed(String name, int length, int scale) {
            return add(name, length, FieldType.SIGNED, scale);
        }

        public Builder filler(int length) {
            return add("FILLER-" + (++fillers), length, FieldType.ALPHANUMERIC, 0);
        }

        private Builder add(String name, int length, FieldType type, int scale) {
            fields.put(name, new FieldSpec(name, offset, length, type, scale));
            offset += length;
            return this;
        }

        public RecordLayout build() {
            return new RecordLayout(copybook, offset, new LinkedHashMap<>(fields));
        }
    }
}
