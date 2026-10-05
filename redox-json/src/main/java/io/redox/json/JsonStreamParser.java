package io.redox.json;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Single-pass streaming JSON parser for zero-allocation POJO binding.
 *
 * Drives APT-generated {@link StreamBindable} converters through the source bytes
 * without building any intermediate token tape.  Allocations per parse are limited
 * to the POJO objects and decoded String values — nothing else.
 *
 * Usage: the generated converter calls {@link #nextToken()} to advance, then reads
 * the current value via {@link #getLong()}, {@link #getDouble()}, {@link #getString()},
 * or {@link #keyMatchesBytes(byte[])} (zero-alloc key comparison).
 */
public final class JsonStreamParser {

    // ── Token type ────────────────────────────────────────────────────────

    public enum Token {
        START_OBJECT, END_OBJECT,
        START_ARRAY,  END_ARRAY,
        FIELD_KEY,       // string that is the key of an object property
        VALUE_STRING,    // string value
        VALUE_INT,       // integer value  (also readable via getDouble)
        VALUE_FLOAT,     // floating-point (also readable via getLong with truncation)
        VALUE_NULL,
        VALUE_BOOL_TRUE,
        VALUE_BOOL_FALSE,
        EOF
    }

    // ── Source buffer ─────────────────────────────────────────────────────

    private final byte[] src;
    private       int    pos;
    private final int    end;

    // ── Current token state ───────────────────────────────────────────────

    private Token  current;
    private int    strOffset;
    private int    strLen;
    private boolean strHasEscapes;
    private long   longVal;
    private double doubleVal;

    // ── Nesting stack ─────────────────────────────────────────────────────
    // Each entry: 0 = ARRAY context, 1 = OBJECT context
    // atObjectKey tracks whether the next string token is a FIELD_KEY.

    private int[]   nestType  = new int[32];
    private int     nestDepth = -1;
    private boolean atObjectKey = false;

    private static final int CTX_ARRAY  = 0;
    private static final int CTX_OBJECT = 1;

    // ── Constructor ───────────────────────────────────────────────────────

    public JsonStreamParser(byte[] src, int offset, int length) {
        this.src = src;
        this.pos = offset;
        this.end = offset + length;
        skipUtf8Bom();
    }

    private void skipUtf8Bom() {
        if (end - pos >= 3
                && src[pos] == (byte)0xEF
                && src[pos+1] == (byte)0xBB
                && src[pos+2] == (byte)0xBF) {
            pos += 3;
        }
    }

    // ── Core API ──────────────────────────────────────────────────────────

    /** Advance to the next token and return its type. */
    public Token nextToken() {
        pos = VectorScanner.skipWhitespace(src, pos, end);
        if (pos >= end) return current = Token.EOF;

        byte b = src[pos];

        switch (b) {

            case '{':
                pos++;
                if (++nestDepth >= nestType.length) growNest();
                nestType[nestDepth] = CTX_OBJECT;
                atObjectKey = true;
                return current = Token.START_OBJECT;

            case '}':
                pos++;
                nestDepth--;
                atObjectKey = false; // just finished a value; ',' will restore if needed
                return current = Token.END_OBJECT;

            case '[':
                pos++;
                if (++nestDepth >= nestType.length) growNest();
                nestType[nestDepth] = CTX_ARRAY;
                atObjectKey = false;
                return current = Token.START_ARRAY;

            case ']':
                pos++;
                nestDepth--;
                atObjectKey = false;
                return current = Token.END_ARRAY;

            case ',':
                pos++;
                // after a value separator in an object, next token is a key
                if (nestDepth >= 0 && nestType[nestDepth] == CTX_OBJECT) atObjectKey = true;
                return nextToken();

            case ':':
                pos++;
                return nextToken(); // colon is consumed silently

            case '"':
                return readString();

            case 'n':
                pos += 4; // "null"
                return current = Token.VALUE_NULL;

            case 't':
                pos += 4; // "true"
                return current = Token.VALUE_BOOL_TRUE;

            case 'f':
                pos += 5; // "false"
                return current = Token.VALUE_BOOL_FALSE;

            default:
                if (b == '-' || (b >= '0' && b <= '9')) return readNumber();
                throw new JsonParseException("Unexpected byte 0x"
                        + Integer.toHexString(b & 0xFF) + " at offset " + pos, pos);
        }
    }

    public Token currentToken() { return current; }

    // ── Key comparison (zero-allocation hot path) ─────────────────────────

    /**
     * Returns true if the current FIELD_KEY's raw source bytes equal {@code expected}.
     * For unescaped keys (the vast majority) this is a direct byte comparison via
     * {@link Arrays#equals(byte[], int, int, byte[], int, int)} — no String allocation.
     */
    public boolean keyMatchesBytes(byte[] expected) {
        if (strLen != expected.length) return false;
        if (!strHasEscapes) {
            return Arrays.equals(src, strOffset, strOffset + strLen, expected, 0, expected.length);
        }
        // rare: escaped key — decode and compare
        return JsonDocument.unescape(src, strOffset, strLen)
                .equals(new String(expected, StandardCharsets.UTF_8));
    }

    // ── Value accessors ───────────────────────────────────────────────────

    /** Decode the current string token (FIELD_KEY or VALUE_STRING) to a Java String. */
    public String getString() {
        if (!strHasEscapes) return new String(src, strOffset, strLen, StandardCharsets.UTF_8);
        return JsonDocument.unescape(src, strOffset, strLen);
    }

    /**
     * Get the current string value, coercing numbers/booleans to String
     * (needed when a JSON number appears where a POJO String field is expected).
     */
    public String getStringCoerced() {
        switch (current) {
            case VALUE_STRING: return getString();
            case VALUE_NULL:   return null;
            case VALUE_INT:    return Long.toString(longVal);
            case VALUE_FLOAT:  return Double.toString(doubleVal);
            case VALUE_BOOL_TRUE:  return "true";
            case VALUE_BOOL_FALSE: return "false";
            default: return null;
        }
    }

    public long    getLong()    { return longVal; }
    public double  getDouble()  { return (current == Token.VALUE_INT) ? (double) longVal : doubleVal; }
    public int     getInt()     { return (int) longVal; }
    public boolean getBoolean() { return current == Token.VALUE_BOOL_TRUE; }

    // Raw access to current string token's position (for advanced callers)
    public int     strOffset()      { return strOffset; }
    public int     strLen()         { return strLen; }
    public boolean strHasEscapes()  { return strHasEscapes; }
    public byte[]  source()         { return src; }

    // ── Skip ──────────────────────────────────────────────────────────────

    /**
     * Skip the current value.  If it is a scalar, does nothing (already consumed).
     * If it is START_OBJECT or START_ARRAY, consumes tokens until the matching END.
     */
    public void skipValue() {
        if (current == Token.START_OBJECT || current == Token.START_ARRAY) {
            int depth = 1;
            while (depth > 0) {
                Token t = nextToken();
                if (t == Token.START_OBJECT || t == Token.START_ARRAY) depth++;
                else if (t == Token.END_OBJECT || t == Token.END_ARRAY) depth--;
                else if (t == Token.EOF) break;
            }
        }
        // scalars already fully consumed
    }

    // ── Private parsing helpers ───────────────────────────────────────────

    private Token readString() {
        pos++; // skip opening '"'
        strOffset = pos;
        strHasEscapes = false;

        // ── Vector / SWAR string scanner ─────────────────────────────────────
        // VectorScanner.nextSpecial() returns the position of the first '"' or '\\'
        // in src[pos..end), processing VLEN bytes per cycle (32 on AVX2 CPUs).
        // After each escape we restart the scanner so inter-escape spans also benefit.
        OUTER:
        while (pos < end) {
            pos = VectorScanner.nextSpecial(src, pos, end);
            if (pos >= end) break;       // no closing '"' — malformed JSON
            byte c = src[pos];
            if (c == '"') break OUTER;   // found closing quote
            // c == '\\': escape sequence
            strHasEscapes = true;
            pos += 2;                    // skip '\\' + escaped char, then restart scanner
        }

        strLen = pos - strOffset;
        pos++; // skip closing '"'

        if (atObjectKey) {
            atObjectKey = false; // consumed the key; next is ':'
            return current = Token.FIELD_KEY;
        }
        return current = Token.VALUE_STRING;
    }

    private Token readNumber() {
        int start = pos;
        boolean isFloat = false;
        if (src[pos] == '-') pos++;
        while (pos < end && src[pos] >= '0' && src[pos] <= '9') pos++;
        if (pos < end && src[pos] == '.') {
            isFloat = true; pos++;
            while (pos < end && src[pos] >= '0' && src[pos] <= '9') pos++;
        }
        if (pos < end && (src[pos] == 'e' || src[pos] == 'E')) {
            isFloat = true; pos++;
            if (pos < end && (src[pos] == '+' || src[pos] == '-')) pos++;
            while (pos < end && src[pos] >= '0' && src[pos] <= '9') pos++;
        }

        if (isFloat) {
            doubleVal = Double.parseDouble(
                    new String(src, start, pos - start, StandardCharsets.US_ASCII));
            return current = Token.VALUE_FLOAT;
        } else {
            longVal = parseLongFast(src, start, pos - start);
            doubleVal = (double) longVal;
            return current = Token.VALUE_INT;
        }
    }

    private static long parseLongFast(byte[] src, int offset, int length) {
        boolean neg = src[offset] == '-';
        int i = neg ? offset + 1 : offset;
        long v = 0;
        int end = offset + length;
        while (i < end) v = v * 10 + (src[i++] - '0');
        return neg ? -v : v;
    }

    private void growNest() {
        nestType = Arrays.copyOf(nestType, nestType.length * 2);
    }
}
