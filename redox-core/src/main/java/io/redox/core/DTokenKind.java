package io.redox.core;

/**
 * 4-bit kind field occupying bits 56-59 of a DToken long.
 * Maps directly to REDox's DTokenKind enum.
 */
public enum DTokenKind {
    CONTROL    (0b0000),
    TRIVIA     (0b0001),
    BOOLEAN    (0b0010),
    NULL       (0b0011),
    INTEGER    (0b0100),
    FLOAT      (0b0101),
    BIG_NUMBER (0b0110),
    INLINE_FLOAT(0b0111),
    STRING     (0b1000),
    SYMBOL     (0b1001),
    BYTE_STRING(0b1010),
    TIMESTAMP  (0b1011);

    public final int value;

    DTokenKind(int value) { this.value = value; }

    private static final DTokenKind[] BY_VALUE = new DTokenKind[16];
    static {
        for (DTokenKind k : values()) BY_VALUE[k.value] = k;
    }

    public static DTokenKind fromBits(int bits) { return BY_VALUE[bits & 0xF]; }
}
