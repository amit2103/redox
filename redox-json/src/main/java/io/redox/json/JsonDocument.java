package io.redox.json;

import io.redox.core.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Parses UTF-8 JSON into a REDox token tape.
 *
 * The parser is a single-pass state machine mirroring the C# ParseJson() logic:
 * it walks source bytes once, allocating tokens into the tape without building
 * any intermediate tree.  String tokens store (length, offset) into the source
 * buffer so decoding is lazy.
 *
 * Usage:
 *   try (var doc = JsonDocument.parse("{\"x\":1}")) {
 *       DElement root = doc.root();
 *       int x = root.get("x").getInt();  // => 1
 *   }
 */
public final class JsonDocument extends io.redox.core.Document {

    // ── Parse entry points ────────────────────────────────────────────────

    public static JsonDocument parse(String json) {
        return parse(json.getBytes(StandardCharsets.UTF_8));
    }

    public static JsonDocument parse(byte[] utf8) {
        return parse(utf8, 0, utf8.length);
    }

    public static JsonDocument parse(byte[] utf8, int offset, int length) {
        JsonDocument doc = new JsonDocument();
        doc.source    = utf8;
        doc.sourceLen = length;
        doc.parseJson(utf8, offset, length);
        return doc;
    }

    public static JsonDocument parse(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        return parse(bytes);
    }

    // ── Root accessor ─────────────────────────────────────────────────────

    public DElement root() {
        return elementAt(rootId);
    }

    // ── Core parser ──────────────────────────────────────────────────────

    /**
     * Single-pass JSON parser.  Allocates tokens directly into the tape.
     *
     * State is tracked with a small stack of (containerId, pairCount) pairs.
     * The stack is heap-allocated but short (one entry per nesting level).
     */
    private void parseJson(byte[] src, int start, int len) {
        // Stack: alternating [containerId, elementCount] for nested containers
        int[] stack    = new int[64];  // grows if needed
        int   stackTop = -1;           // index of current container's id slot

        int i   = start;
        int end = start + len;

        // skip UTF-8 BOM if present
        if (len >= 3 && src[i] == (byte)0xEF && src[i+1] == (byte)0xBB && src[i+2] == (byte)0xBF) {
            i += 3;
        }

        // allocate root sentinel — will be replaced by the first real value
        // token 0 = NONE (reserved), actual parse starts filling from index 1

        while (i < end) {
            byte b = src[i];

            // ── Whitespace ────────────────────────────────────────────
            if (b == ' ' || b == '\t' || b == '\n' || b == '\r') { i++; continue; }

            // ── String ───────────────────────────────────────────────
            if (b == '"') {
                i++; // skip opening quote
                int strStart = i;
                int variant  = DTokenVariant.STRING; // assume no escapes

                // scan for closing quote or backslash
                while (i < end) {
                    byte c = src[i];
                    if (c == '"') break;
                    if (c == '\\') { variant = DTokenVariant.STRING_DOUBLE_QUOTE; i++; } // skip escaped char
                    i++;
                }
                int strLen = i - strStart;
                i++; // skip closing quote

                long payload = DToken.encodeLengthOffset(strLen, strStart);
                int tokenId  = allocToken(DToken.make(variant, payload));
                notifyToken(tokenId, stack, stackTop);
                continue;
            }

            // ── Array open ────────────────────────────────────────────
            if (b == '[') {
                i++;
                int tokenId = allocToken(DToken.makeArray(0, 0));
                notifyToken(tokenId, stack, stackTop);
                // push onto stack
                stackTop += 2;
                if (stackTop >= stack.length) stack = growStack(stack);
                stack[stackTop - 1] = tokenId;
                stack[stackTop]     = 0; // element count
                continue;
            }

            // ── Array close ───────────────────────────────────────────
            if (b == ']') {
                i++;
                if (stackTop < 1) throw new JsonParseException("Unexpected ']'", i);
                int containerId = stack[stackTop - 1];
                int count       = stack[stackTop];
                stackTop -= 2;
                // back-patch count into the array token
                long arr = tokens[containerId];
                long kind    = arr & 0x7000_0000_0000_0000L;
                long linkBits= arr & 0x0FFF_FFFF_0000_0000L;
                tokens[containerId] = DToken.EXTENSION_BIT | kind | linkBits | (count & 0x3FFF_FFFFL);
                // record skip-id so nextToken() can jump past this array
                setSkipId(containerId, tokenCount);
                continue;
            }

            // ── Object open ───────────────────────────────────────────
            if (b == '{') {
                i++;
                int tokenId = allocToken(DToken.makeMap(0, 0));
                notifyToken(tokenId, stack, stackTop);
                stackTop += 2;
                if (stackTop >= stack.length) stack = growStack(stack);
                stack[stackTop - 1] = tokenId;
                stack[stackTop]     = 0;
                continue;
            }

            // ── Object close ──────────────────────────────────────────
            if (b == '}') {
                i++;
                if (stackTop < 1) throw new JsonParseException("Unexpected '}'", i);
                int containerId = stack[stackTop - 1];
                int count       = stack[stackTop];
                stackTop -= 2;
                long map  = tokens[containerId];
                long kind    = map & 0x7000_0000_0000_0000L;
                long linkBits= map & 0x0FFF_FFFF_0000_0000L;
                tokens[containerId] = DToken.EXTENSION_BIT | kind | linkBits | (count & 0x3FFF_FFFFL);
                setSkipId(containerId, tokenCount);
                continue;
            }

            // ── Comma / colon (structural, not tokenised) ─────────────
            if (b == ',' || b == ':') { i++; continue; }

            // ── null ─────────────────────────────────────────────────
            if (b == 'n') {
                if (i + 3 < end && src[i+1]=='u' && src[i+2]=='l' && src[i+3]=='l') {
                    i += 4;
                    int tokenId = allocToken(DToken.NULL);
                    notifyToken(tokenId, stack, stackTop);
                    continue;
                }
                throw new JsonParseException("Invalid literal at " + i, i);
            }

            // ── true ─────────────────────────────────────────────────
            if (b == 't') {
                if (i + 3 < end && src[i+1]=='r' && src[i+2]=='u' && src[i+3]=='e') {
                    i += 4;
                    int tokenId = allocToken(DToken.TRUE);
                    notifyToken(tokenId, stack, stackTop);
                    continue;
                }
                throw new JsonParseException("Invalid literal at " + i, i);
            }

            // ── false ────────────────────────────────────────────────
            if (b == 'f') {
                if (i + 4 < end && src[i+1]=='a' && src[i+2]=='l' && src[i+3]=='s' && src[i+4]=='e') {
                    i += 5;
                    int tokenId = allocToken(DToken.FALSE);
                    notifyToken(tokenId, stack, stackTop);
                    continue;
                }
                throw new JsonParseException("Invalid literal at " + i, i);
            }

            // ── Number ───────────────────────────────────────────────
            if (b == '-' || (b >= '0' && b <= '9')) {
                int numStart = i;
                boolean isFloat = false;
                if (b == '-') i++;
                while (i < end && src[i] >= '0' && src[i] <= '9') i++;
                if (i < end && src[i] == '.') { isFloat = true; i++;
                    while (i < end && src[i] >= '0' && src[i] <= '9') i++; }
                if (i < end && (src[i] == 'e' || src[i] == 'E')) { isFloat = true; i++;
                    if (i < end && (src[i] == '+' || src[i] == '-')) i++;
                    while (i < end && src[i] >= '0' && src[i] <= '9') i++; }

                int numLen = i - numStart;
                long payload = DToken.encodeLengthOffset(numLen, numStart);
                int variant  = isFloat ? DTokenVariant.FLOAT : DTokenVariant.INTEGER;
                int tokenId  = allocToken(DToken.make(variant, payload));
                notifyToken(tokenId, stack, stackTop);
                continue;
            }

            throw new JsonParseException("Unexpected byte 0x" + Integer.toHexString(b & 0xFF) + " at " + i, i);
        }

        rootId = 1;
    }

    /**
     * Called after each value token is allocated.
     * If we're inside a container, increments its element count.
     * For objects, pairs of tokens (key+value) are counted together — we
     * count each token individually; the DObject.size() divides by 2.
     */
    private void notifyToken(int tokenId, int[] stack, int stackTop) {
        if (stackTop >= 1) {
            stack[stackTop]++; // increment count of parent container
        }
    }

    private static int[] growStack(int[] stack) {
        int[] grown = new int[stack.length * 2];
        System.arraycopy(stack, 0, grown, 0, stack.length);
        return grown;
    }

    // ── Decode implementations ────────────────────────────────────────────

    @Override
    public String decodeString(int id, long token) {
        long payload = DToken.payload(token);
        int  offset  = DToken.decodeOffset(payload);
        int  length  = DToken.decodeLength(payload);
        int  variant = DToken.variant(token);

        if (variant == DTokenVariant.STRING) {
            // no escapes — fast path
            return new String(source, offset, length, StandardCharsets.UTF_8);
        }
        // escape-sequence path
        return unescape(source, offset, length);
    }

    @Override
    public long decodeInteger(int id, long token) {
        long payload = DToken.payload(token);
        int  offset  = DToken.decodeOffset(payload);
        int  length  = DToken.decodeLength(payload);

        if (DToken.isTrue(token))  return 1L;
        if (DToken.isFalse(token)) return 0L;

        // parse from source bytes — avoids String allocation on the fast path
        return parseLong(source, offset, length);
    }

    @Override
    public double decodeFloat(int id, long token) {
        if (DToken.isArray(token) || DToken.isMap(token))
            throw new IllegalStateException("Cannot decode container as number");

        int variant = DToken.variant(token);
        if (variant == DTokenVariant.INLINE_FLOAT) return DToken.inlineFloatValue(token);

        long payload = DToken.payload(token);
        int  offset  = DToken.decodeOffset(payload);
        int  length  = DToken.decodeLength(payload);
        return Double.parseDouble(new String(source, offset, length, StandardCharsets.US_ASCII));
    }

    // ── Static decode helpers ────────────────────────────────────────────

    /** Parse a long integer directly from UTF-8 bytes — no String allocation. */
    private static long parseLong(byte[] src, int offset, int length) {
        boolean negative = false;
        int i = offset;
        if (src[i] == '-') { negative = true; i++; }
        long result = 0;
        int end = offset + length;
        while (i < end) {
            result = result * 10 + (src[i++] - '0');
        }
        return negative ? -result : result;
    }

    /** JSON string unescape — handles \", \\, \/, \b, \f, \n, \r, \t, \\uXXXX. */
    static String unescape(byte[] src, int offset, int length) {
        byte[] buf = new byte[length]; // worst case same size
        int out = 0;
        int end = offset + length;
        int i   = offset;
        while (i < end) {
            byte b = src[i++];
            if (b != '\\') {
                buf[out++] = b;
                continue;
            }
            // escape sequence
            byte esc = src[i++];
            switch (esc) {
                case '"':  buf[out++] = '"';  break;
                case '\\': buf[out++] = '\\'; break;
                case '/':  buf[out++] = '/';  break;
                case 'b':  buf[out++] = '\b'; break;
                case 'f':  buf[out++] = '\f'; break;
                case 'n':  buf[out++] = '\n'; break;
                case 'r':  buf[out++] = '\r'; break;
                case 't':  buf[out++] = '\t'; break;
                case 'u': {
                    // 4 hex digits → code point → UTF-8
                    int cp = parseHex4(src, i);
                    i += 4;
                    // handle surrogate pairs
                    if (cp >= 0xD800 && cp <= 0xDBFF && i + 5 < end
                            && src[i] == '\\' && src[i+1] == 'u') {
                        int low = parseHex4(src, i + 2);
                        if (low >= 0xDC00 && low <= 0xDFFF) {
                            cp = 0x10000 + ((cp - 0xD800) << 10) + (low - 0xDC00);
                            i += 6;
                        }
                    }
                    out = appendCodePoint(buf, out, cp);
                    break;
                }
                default: buf[out++] = esc;
            }
        }
        return new String(buf, 0, out, StandardCharsets.UTF_8);
    }

    private static int parseHex4(byte[] src, int i) {
        return (hexVal(src[i]) << 12) | (hexVal(src[i+1]) << 8)
             | (hexVal(src[i+2]) << 4) |  hexVal(src[i+3]);
    }

    private static int hexVal(byte b) {
        if (b >= '0' && b <= '9') return b - '0';
        if (b >= 'a' && b <= 'f') return b - 'a' + 10;
        if (b >= 'A' && b <= 'F') return b - 'A' + 10;
        throw new JsonParseException("Invalid hex digit: " + (char)b, 0);
    }

    /** Encode a Unicode code point as UTF-8 into buf[out..], return new out. */
    private static int appendCodePoint(byte[] buf, int out, int cp) {
        if (cp < 0x80) {
            buf[out++] = (byte) cp;
        } else if (cp < 0x800) {
            buf[out++] = (byte)(0xC0 | (cp >> 6));
            buf[out++] = (byte)(0x80 | (cp & 0x3F));
        } else if (cp < 0x10000) {
            buf[out++] = (byte)(0xE0 | (cp >> 12));
            buf[out++] = (byte)(0x80 | ((cp >> 6) & 0x3F));
            buf[out++] = (byte)(0x80 | (cp & 0x3F));
        } else {
            buf[out++] = (byte)(0xF0 | (cp >> 18));
            buf[out++] = (byte)(0x80 | ((cp >> 12) & 0x3F));
            buf[out++] = (byte)(0x80 | ((cp >> 6) & 0x3F));
            buf[out++] = (byte)(0x80 | (cp & 0x3F));
        }
        return out;
    }
}
