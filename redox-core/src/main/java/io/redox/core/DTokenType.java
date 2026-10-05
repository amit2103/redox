package io.redox.core;

/**
 * 3-bit type field occupying bits 60-62 of a DToken long.
 * Maps directly to REDox's DTokenType enum.
 */
public enum DTokenType {
    IGNORE   (0b000),
    LITERAL  (0b001),
    NUMBER   (0b010),
    EXT_NUMBER(0b011),
    TEXT     (0b100),
    BINARY   (0b101),
    MAP      (0b110),
    ARRAY    (0b111);

    public final int value;

    DTokenType(int value) { this.value = value; }

    private static final DTokenType[] BY_VALUE = new DTokenType[8];
    static {
        for (DTokenType t : values()) BY_VALUE[t.value] = t;
    }

    public static DTokenType fromBits(int bits) { return BY_VALUE[bits & 0x7]; }
}
