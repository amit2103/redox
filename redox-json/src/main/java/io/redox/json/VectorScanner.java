package io.redox.json;

import jdk.incubator.vector.*;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * Hardware-SIMD string scanner using the Java Vector API (jdk.incubator.vector).
 *
 * {@link #nextSpecial(byte[], int, int)} finds the first {@code '"'} or {@code '\\'}
 * in a byte array using {@link ByteVector} — a single VPCMPEQB instruction compares
 * 32 bytes in parallel on AVX2.  Falls back automatically to SWAR (8 bytes/cycle)
 * when fewer than {@code SPECIES.length()} bytes remain, then byte-by-byte for the tail.
 *
 * {@link #nextSpecialSeg(MemorySegment, int, int)} is the same algorithm but reads
 * from a {@link MemorySegment} — used when the source bytes are already wrapped.
 */
public final class VectorScanner {

    // ── Species selection ─────────────────────────────────────────────────
    // SPECIES_PREFERRED picks the widest ISA the JVM/CPU exposes:
    //   AVX-512 → 64 bytes/iter   AVX2 → 32   SSE2/NEON → 16

    static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    static final int                 VLEN    = SPECIES.length();   // bytes per vector

    // Pre-broadcast sentinel constants (created once, reused forever)
    private static final ByteVector V_QUOTE = ByteVector.broadcast(SPECIES, (byte) '"');   // 0x22
    private static final ByteVector V_SLASH = ByteVector.broadcast(SPECIES, (byte) '\\');  // 0x5C

    private VectorScanner() {}

    // ── Main API ──────────────────────────────────────────────────────────

    /**
     * Returns the index of the first {@code '"'} or {@code '\\'} in
     * {@code src[pos..end)}, or {@code end} if none is found.
     *
     * <p>Hot path: each full-vector iteration loads {@value #VLEN} bytes,
     * compares against {@code '"'} and {@code '\\'} in parallel, and uses
     * {@link VectorMask#firstTrue()} (a single BSF/TZCNT instruction) to locate
     * the hit.  No branch per byte.
     */
    public static int nextSpecial(byte[] src, int pos, int end) {
        // ── Vector loop ───────────────────────────────────────────────────
        int limit = end - VLEN;
        while (pos <= limit) {
            ByteVector v    = ByteVector.fromArray(SPECIES, src, pos);
            VectorMask<Byte> hit = v.eq(V_QUOTE).or(v.eq(V_SLASH));
            if (hit.anyTrue()) return pos + hit.firstTrue();
            pos += VLEN;
        }

        // ── SWAR tail (< VLEN bytes remaining) ────────────────────────────
        if (UnsafeAccess.SWAR_ENABLED) {
            int swarLimit = end - 8;
            while (pos <= swarLimit) {
                long w   = MemorySegment.ofArray(src).get(ValueLayout.JAVA_LONG_UNALIGNED, pos);
                int  hit = UnsafeAccess.firstByte(
                        UnsafeAccess.swarMatch(w, 0x22L),
                        UnsafeAccess.swarMatch(w, 0x5CL));
                if (hit < 8) return pos + hit;
                pos += 8;
            }
        }

        // ── Scalar tail ───────────────────────────────────────────────────
        while (pos < end) {
            byte b = src[pos];
            if (b == '"' || b == '\\') return pos;
            pos++;
        }
        return end;
    }

    /**
     * Same as {@link #nextSpecial(byte[], int, int)} but reads from a
     * {@link MemorySegment}.  Used in the tape-based parser where the source
     * is already a segment.
     */
    public static int nextSpecialSeg(MemorySegment seg, int pos, int end) {
        int limit = end - VLEN;
        while (pos <= limit) {
            ByteVector v    = ByteVector.fromMemorySegment(SPECIES, seg, pos, ByteOrder.nativeOrder());
            VectorMask<Byte> hit = v.eq(V_QUOTE).or(v.eq(V_SLASH));
            if (hit.anyTrue()) return pos + hit.firstTrue();
            pos += VLEN;
        }
        // Scalar tail
        while (pos < end) {
            byte b = (byte) seg.get(ValueLayout.JAVA_BYTE, pos);
            if (b == '"' || b == '\\') return pos;
            pos++;
        }
        return end;
    }

    // ── Whitespace skip ───────────────────────────────────────────────────

    /**
     * Returns the first position >= {@code pos} where {@code src[i]} is not
     * ASCII whitespace ({@code ' '}, {@code '\t'}, {@code '\n'}, {@code '\r'}).
     *
     * <p>Uses the Vector API to test four whitespace values in parallel across
     * {@value #VLEN} bytes per iteration.
     */
    public static int skipWhitespace(byte[] src, int pos, int end) {
        if (VLEN >= 16) {
            ByteVector vSp = ByteVector.broadcast(SPECIES, (byte) ' ');
            ByteVector vTb = ByteVector.broadcast(SPECIES, (byte) '\t');
            ByteVector vNl = ByteVector.broadcast(SPECIES, (byte) '\n');
            ByteVector vCr = ByteVector.broadcast(SPECIES, (byte) '\r');

            int limit = end - VLEN;
            while (pos <= limit) {
                ByteVector v = ByteVector.fromArray(SPECIES, src, pos);
                // a byte is whitespace if it equals any of the four WS chars
                VectorMask<Byte> ws = v.eq(vSp).or(v.eq(vTb)).or(v.eq(vNl)).or(v.eq(vCr));
                // first NON-whitespace byte is first where ws is false
                VectorMask<Byte> nonWs = ws.not();
                if (nonWs.anyTrue()) return pos + nonWs.firstTrue();
                pos += VLEN;
            }
        }
        while (pos < end) {
            byte b = src[pos];
            if (b != ' ' && b != '\t' && b != '\n' && b != '\r') return pos;
            pos++;
        }
        return end;
    }
}
