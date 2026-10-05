package io.redox.json;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Low-level memory helpers.
 *
 * On JDK 22+ all reads go through {@link MemorySegment} (stable Panama API).
 * {@link sun.misc.Unsafe} is kept as a static fallback for SWAR reads only
 * (same bit-trick, just accessed via MemorySegment now).
 *
 * SWAR layout: reads return native byte order.  On little-endian x86
 * {@code src[pos]} is the LSB, so {@link Long#numberOfTrailingZeros}/8 gives
 * the byte offset of the first match.
 */
public final class UnsafeAccess {

    public static final boolean SWAR_ENABLED = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    // Kept for the SWAR helpers — MemorySegment.ofArray().get() is the canonical read
    // but we also expose the raw long helpers used by VectorScanner's tail path.
    static final long BYTE_ARRAY_BASE;
    static final sun.misc.Unsafe UNSAFE;

    static {
        sun.misc.Unsafe u = null;
        long base = 0L;
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u    = (sun.misc.Unsafe) f.get(null);
            base = u.arrayBaseOffset(byte[].class);
        } catch (Exception ignored) {}
        UNSAFE          = u;
        BYTE_ARRAY_BASE = base;
    }

    private UnsafeAccess() {}

    // ── MemorySegment long read (replaces Unsafe.getLong) ─────────────────

    /**
     * Read 8 bytes from {@code src} at {@code pos} as a native-byte-order long.
     * Uses {@link MemorySegment} (stable Panama API) rather than Unsafe.
     */
    public static long getLong(byte[] src, int pos) {
        return MemorySegment.ofArray(src).get(ValueLayout.JAVA_LONG_UNALIGNED, pos);
    }

    // ── SWAR primitives ───────────────────────────────────────────────────

    public static long swarMatch(long word, long v) {
        long x = word ^ (0x0101_0101_0101_0101L * v);
        return (x - 0x0101_0101_0101_0101L) & ~x & 0x8080_8080_8080_8080L;
    }

    public static int firstByte(long hasMatch) {
        return hasMatch == 0L ? 8 : (Long.numberOfTrailingZeros(hasMatch) >>> 3);
    }

    public static int firstByte(long matchA, long matchB) {
        long both = matchA | matchB;
        return both == 0L ? 8 : (Long.numberOfTrailingZeros(both) >>> 3);
    }

    // ── VarHandle field-offset helper ─────────────────────────────────────

    /**
     * Returns the field offset via Unsafe (used by generated code as fallback
     * when VarHandle is unavailable).  Returns -1 on failure.
     */
    public static long fieldOffset(Class<?> cls, String fieldName) {
        if (UNSAFE == null) return -1L;
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try { return UNSAFE.objectFieldOffset(c.getDeclaredField(fieldName)); }
            catch (NoSuchFieldException ignored) { c = c.getSuperclass(); }
        }
        return -1L;
    }
}
