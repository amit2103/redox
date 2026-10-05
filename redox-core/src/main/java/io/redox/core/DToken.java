package io.redox.core;

/**
 * Pure-static utility for encoding/decoding REDox 64-bit tokens.
 *
 * Bit layout (matches C# REDox exactly):
 *
 *   Bit 63      : extension bit  (1 = extended/control token)
 *   Bits 60-62  : DTokenType     (3 bits)
 *   Bits 56-59  : DTokenKind sub (4 bits)  } together = 7-bit variant in bits 56-62
 *   Bits  0-55  : payload        (56 bits)
 *
 * Extended tokens (bit 63 = 1) use a different layout:
 *   Bits 60-62  : control sub-kind
 *   Bits  0-59  : extended payload (jump targets, link ids, counts)
 */
public final class DToken {

    private DToken() {}

    // ── Masks ────────────────────────────────────────────────────────────

    /** Sign bit — set means this is an extended/control token. */
    public static final long EXTENSION_BIT  = Long.MIN_VALUE;            // 0x8000_0000_0000_0000

    /** 7-bit variant field (type+kind), bits 56-62. */
    public static final long VARIANT_MASK   = 0x7F00_0000_0000_0000L;

    /** 56-bit payload, bits 0-55. */
    public static final long PAYLOAD_MASK   = 0x00FF_FFFF_FFFF_FFFFL;

    /** Inline-float payload uses bits 0-58 (no variant bits). */
    public static final long INLINE_FLOAT_MASK = 0x07FF_FFFF_FFFF_FFFFL;

    // Extended token sub-fields (when EXTENSION_BIT is set)
    private static final long EXT_KIND_MASK   = 0x7000_0000_0000_0000L;
    private static final int  EXT_KIND_SHIFT  = 60;

    private static final long EXT_COUNT_MASK  = 0x0000_0000_3FFF_FFFFL; // 30 bits
    private static final long EXT_LINK_MASK   = 0x0FFF_FFFF_0000_0000L; // 28 bits
    private static final int  EXT_LINK_SHIFT  = 32;
    private static final long EXT_JUMP_MASK   = 0x0000_0000_3FFF_FFFFL; // 30 bits

    // Extended sub-kinds
    private static final long EXT_KIND_ARRAY  = 0L;
    private static final long EXT_KIND_MAP    = 1L;
    private static final long EXT_KIND_JUMP   = 2L;

    // Variant field position
    private static final int VARIANT_SHIFT = 56;

    // ── Sentinel ─────────────────────────────────────────────────────────

    public static final long NONE = 0L;

    // ── Factory methods ──────────────────────────────────────────────────

    /** Encodes a variant + 56-bit payload. */
    public static long make(int variant, long payload) {
        return ((long) variant << VARIANT_SHIFT) | (payload & PAYLOAD_MASK);
    }

    /**
     * Array container token.
     * @param count initial element count (≤ 30 bits)
     * @param linkId next-sibling link id (≤ 28 bits), 0 if none
     */
    public static long makeArray(int count, int linkId) {
        return EXTENSION_BIT
             | (EXT_KIND_ARRAY << EXT_KIND_SHIFT)
             | ((long)(linkId & 0x0FFF_FFFF) << EXT_LINK_SHIFT)
             | (count & EXT_COUNT_MASK);
    }

    /**
     * Map/object container token.
     */
    public static long makeMap(int count, int linkId) {
        return EXTENSION_BIT
             | (EXT_KIND_MAP << EXT_KIND_SHIFT)
             | ((long)(linkId & 0x0FFF_FFFF) << EXT_LINK_SHIFT)
             | (count & EXT_COUNT_MASK);
    }

    /**
     * Jump token — points to another token id (for free-list / indirection).
     */
    public static long makeJump(int jumpId) {
        return EXTENSION_BIT
             | (EXT_KIND_JUMP << EXT_KIND_SHIFT)
             | (jumpId & EXT_JUMP_MASK);
    }

    /**
     * Inline float — stores a double directly in the token's lower 59 bits.
     * The double is stored as its raw IEEE-754 long bits with the top 5 bits cleared.
     */
    public static long makeInlineFloat(double value) {
        long bits = Double.doubleToRawLongBits(value);
        // shift right 5, losing 5 LSBs of mantissa (acceptable approximation)
        return EXTENSION_BIT | (bits >>> 5);
    }

    /** Boolean false token. */
    public static final long FALSE = make(DTokenVariant.BOOLEAN_FALSE, 0L);
    /** Boolean true token. */
    public static final long TRUE  = make(DTokenVariant.BOOLEAN_TRUE,  0L);
    /** JSON null token. */
    public static final long NULL  = make(DTokenVariant.NULL_DEFAULT,  0L);

    // ── Payload encoding for string/number ──────────────────────────────

    /**
     * Encodes (length, sourceOffset) into a 56-bit payload.
     * Layout: bits 55-32 = length (24 bits max ~16 MB), bits 31-0 = offset.
     */
    public static long encodeLengthOffset(int length, int offset) {
        return ((long)(length & 0x00FF_FFFF) << 32) | (offset & 0xFFFF_FFFFL);
    }

    public static int decodeLength(long payload) {
        return (int)((payload >> 32) & 0x00FF_FFFF);
    }

    public static int decodeOffset(long payload) {
        return (int)(payload & 0xFFFF_FFFF);
    }

    // ── Decoders ─────────────────────────────────────────────────────────

    public static boolean isExtended(long token) {
        return token < 0; // sign bit set
    }

    public static boolean isContainer(long token) {
        if (!isExtended(token)) return false;
        long kind = (token & EXT_KIND_MASK) >>> EXT_KIND_SHIFT;
        return kind == EXT_KIND_ARRAY || kind == EXT_KIND_MAP;
    }

    public static boolean isArray(long token) {
        return isExtended(token)
            && ((token & EXT_KIND_MASK) >>> EXT_KIND_SHIFT) == EXT_KIND_ARRAY;
    }

    public static boolean isMap(long token) {
        return isExtended(token)
            && ((token & EXT_KIND_MASK) >>> EXT_KIND_SHIFT) == EXT_KIND_MAP;
    }

    public static boolean isJump(long token) {
        return isExtended(token)
            && ((token & EXT_KIND_MASK) >>> EXT_KIND_SHIFT) == EXT_KIND_JUMP;
    }

    /** Returns the 7-bit variant (type+kind) of a non-extended token. */
    public static int variant(long token) {
        return (int)((token & VARIANT_MASK) >>> VARIANT_SHIFT);
    }

    /** Returns the 4-bit DTokenKind of a non-extended token. */
    public static int kind(long token) {
        return (int)((token >>> (VARIANT_SHIFT + 3)) & 0xF);
    }

    /** Returns the 56-bit payload of a non-extended token. */
    public static long payload(long token) {
        return token & PAYLOAD_MASK;
    }

    public static int containerCount(long token) {
        return (int)(token & EXT_COUNT_MASK);
    }

    public static int containerLinkId(long token) {
        return (int)((token & EXT_LINK_MASK) >>> EXT_LINK_SHIFT);
    }

    public static int jumpTarget(long token) {
        return (int)(token & EXT_JUMP_MASK);
    }

    public static double inlineFloatValue(long token) {
        return Double.longBitsToDouble((token & ~EXTENSION_BIT) << 5);
    }

    // ── Type checks on non-extended tokens ───────────────────────────────

    public static boolean isLiteral(long token) {
        int k = kind(token);
        return k == DTokenKind.BOOLEAN.value || k == DTokenKind.NULL.value;
    }

    public static boolean isNumeric(long token) {
        int k = kind(token);
        return k == DTokenKind.INTEGER.value
            || k == DTokenKind.FLOAT.value
            || k == DTokenKind.BIG_NUMBER.value
            || k == DTokenKind.INLINE_FLOAT.value;
    }

    public static boolean isText(long token) {
        int k = kind(token);
        return k == DTokenKind.STRING.value || k == DTokenKind.SYMBOL.value;
    }

    public static boolean isBinary(long token) {
        int k = kind(token);
        return k == DTokenKind.BYTE_STRING.value || k == DTokenKind.TIMESTAMP.value;
    }

    public static boolean isNull(long token) {
        return !isExtended(token) && kind(token) == DTokenKind.NULL.value;
    }

    public static boolean isTrue(long token)  { return token == TRUE; }
    public static boolean isFalse(long token) { return token == FALSE; }
}
