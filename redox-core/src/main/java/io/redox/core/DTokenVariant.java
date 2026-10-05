package io.redox.core;

/**
 * 7-bit variant = (DTokenKind << 3) | sub-kind, stored in bits 56-62.
 * Each constant's numeric value encodes both kind and subtype for fast dispatch.
 */
public final class DTokenVariant {

    private DTokenVariant() {}

    // ── Control ──────────────────────────────────────────────────────────
    public static final int CONTROL          = (0b0000 << 3) | 0; // jump / structure
    public static final int TRIVIA_DEFAULT   = (0b0001 << 3) | 0;
    public static final int TRIVIA_WHITESPACE= (0b0001 << 3) | 1;
    public static final int TRIVIA_COMMENT   = (0b0001 << 3) | 2;
    public static final int TRIVIA_SEPARATOR = (0b0001 << 3) | 3;

    // ── Literals ─────────────────────────────────────────────────────────
    public static final int BOOLEAN_FALSE    = (0b0010 << 3) | 0;
    public static final int BOOLEAN_TRUE     = (0b0010 << 3) | 1;
    public static final int NULL_DEFAULT     = (0b0011 << 3) | 0;
    public static final int NULL_UNDEFINED   = (0b0011 << 3) | 1;

    // ── Numbers ──────────────────────────────────────────────────────────
    public static final int INTEGER          = (0b0100 << 3) | 0;
    public static final int INTEGER_HEX      = (0b0100 << 3) | 1;
    public static final int INTEGER_OCTAL    = (0b0100 << 3) | 2;
    public static final int INTEGER_BINARY   = (0b0100 << 3) | 3;
    public static final int INTEGER_UNSIGNED = (0b0100 << 3) | 4;

    public static final int FLOAT            = (0b0101 << 3) | 0;
    public static final int FLOAT_HEX        = (0b0101 << 3) | 1;

    public static final int BIG_NUMBER       = (0b0110 << 3) | 0;

    public static final int INLINE_FLOAT     = (0b0111 << 3) | 0;

    // ── Text ─────────────────────────────────────────────────────────────
    /** String with no escapes — payload encodes raw offset+length into source. */
    public static final int STRING                     = (0b1000 << 3) | 0;
    /** String requiring unescape — payload encodes offset+length into source. */
    public static final int STRING_DOUBLE_QUOTE        = (0b1000 << 3) | 1;
    public static final int STRING_SINGLE_QUOTE        = (0b1000 << 3) | 2;
    public static final int STRING_TEMPLATE            = (0b1000 << 3) | 3;
    public static final int STRING_MULTILINE           = (0b1000 << 3) | 4;
    public static final int STRING_MULTILINE_DOUBLE_QUOTE = (0b1000 << 3) | 5;

    public static final int SYMBOL                     = (0b1001 << 3) | 0;
    public static final int SYMBOL_TYPE                = (0b1001 << 3) | 1;
    public static final int SYMBOL_REF                 = (0b1001 << 3) | 2;

    // ── Binary ───────────────────────────────────────────────────────────
    public static final int BYTE_STRING_BASE64     = (0b1010 << 3) | 0;
    public static final int BYTE_STRING_BASE64_URL = (0b1010 << 3) | 1;
    public static final int BYTE_STRING_HEX        = (0b1010 << 3) | 2;
    public static final int BYTE_STRING_GUID       = (0b1010 << 3) | 3;

    public static final int TIMESTAMP              = (0b1011 << 3) | 0;
    public static final int TIMESTAMP_DATE         = (0b1011 << 3) | 1;
    public static final int TIMESTAMP_TIME         = (0b1011 << 3) | 2;
    public static final int TIMESTAMP_DATETIME     = (0b1011 << 3) | 3;

    // ── Helpers ──────────────────────────────────────────────────────────
    public static int kindOf(int variant)    { return (variant >> 3) & 0xF; }
    public static int subKindOf(int variant) { return variant & 0x7; }
}
