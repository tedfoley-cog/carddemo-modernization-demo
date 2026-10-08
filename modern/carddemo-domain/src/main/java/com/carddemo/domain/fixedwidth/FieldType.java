package com.carddemo.domain.fixedwidth;

/** COBOL PICTURE categories supported at the fixed-width boundary. */
public enum FieldType {
    /** {@code PIC X(n)}: left-justified, space padded. */
    ALPHANUMERIC,
    /** {@code PIC 9(n)[V9(m)]}: unsigned zoned decimal. */
    UNSIGNED,
    /** {@code PIC S9(n)[V9(m)]}: signed zoned decimal with trailing overpunch. */
    SIGNED
}
