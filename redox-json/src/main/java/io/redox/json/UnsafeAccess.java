package io.redox.json;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteOrder;

/**
 * Singleton wrapper around {@link sun.misc.Unsafe} with SWAR (SIMD Within A Register)
 * helpers for byte-scanning hot paths.
 *
 * All public fields are {@code static final} so the JIT folds them into generated code
 * as constants — no indirection.
 *
 * SWAR layout: Unsafe reads a {@code long} in native byte order.  On little-endian x86
 * (the only target where we activate SWAR), {@code src[pos]} occupies bits 0-7 (LSB),
 * so {@link Long#numberOfTrailingZeros} / 8 gives the byte offset of the first match.
 */
public final class UnsafeAccess {

    // ── Singleton ─────────────────────────────────────────────────────────

    public static final Unsafe UNSAFE;
    public static final long   BYTE_ARRAY_BASE;
    public static final long   LONG_ARRAY_BASE;

    /** True only when SWAR word reads are safe and correct. */
    public static final boolean SWAR_ENABLED;

    static {
        Unsafe u = null;
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = (Unsafe) f.get(null);
        } catch (Exception ignored) {}
        UNSAFE = u;
        BYTE_ARRAY_BASE = (u != null) ? u.arrayBaseOffset(byte[].class)  : 0L;
        LONG_ARRAY_BASE = (u != null) ? u.arrayBaseOffset(long[].class)  : 0L;
        SWAR_ENABLED    = (u != null) && ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    }

    private UnsafeAccess() {}

    // ── Field-offset helpers ──────────────────────────────────────────────

    /**
     * Returns the Unsafe field offset for {@code fieldName} in {@code cls} (walks hierarchy).
     * Returns {@code -1} if Unsafe is unavailable or the field does not exist.
     */
    public static long fieldOffset(Class<?> cls, String fieldName) {
        if (UNSAFE == null) return -1L;
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                return UNSAFE.objectFieldOffset(c.getDeclaredField(fieldName));
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return -1L;
    }

    // ── SWAR primitives ───────────────────────────────────────────────────

    /**
     * Returns a long with {@code 0x80} in the MSB of every byte that equals {@code v}.
     * Only correct for v in [0, 127] (7-bit values); JSON chars are always ASCII.
     *
     * Algorithm: XOR each byte with v so matching bytes become 0, then detect zero bytes
     * with the standard Hacker's-Delight trick.
     */
    public static long swarMatch(long word, long v) {
        long x = word ^ (0x0101_0101_0101_0101L * v);
        return (x - 0x0101_0101_0101_0101L) & ~x & 0x8080_8080_8080_8080L;
    }

    /**
     * Returns the byte index (0–7) of the first match inside a {@link #swarMatch} result,
     * or {@code 8} if {@code hasMatch} is zero.
     */
    public static int firstByte(long hasMatch) {
        return hasMatch == 0L ? 8 : (Long.numberOfTrailingZeros(hasMatch) >>> 3);
    }

    /**
     * Combines two match words and returns the first byte offset across both.
     * Equivalent to {@code Math.min(firstByte(a), firstByte(b))} but branch-free.
     */
    public static int firstByte(long matchA, long matchB) {
        long both = matchA | matchB;
        return both == 0L ? 8 : (Long.numberOfTrailingZeros(both) >>> 3);
    }
}
