package io.redox.core;

import java.util.NoSuchElementException;

/**
 * A lightweight, version-checked handle to one token in a Document.
 *
 * Mirrors C# REDox's DElement readonly-struct.  In Java we cannot stack-allocate
 * objects, but on hot read-only paths the JVM escape analyser eliminates many
 * heap allocations for short-lived DElements.
 */
public final class DElement {

    public static final DElement UNDEFINED = new DElement(null, 0, -1);

    final Document doc;
    final int      id;
    final int      version;  // snapshot of doc.version() at creation

    DElement(Document doc, int id, int version) {
        this.doc     = doc;
        this.id      = id;
        this.version = version;
    }

    // ── Validity ─────────────────────────────────────────────────────────

    public Document document() { return doc; }
    public int      tokenId()  { return id; }

    public boolean isValid() {
        return doc != null && id != 0 && doc.version() == version;
    }

    private void checkValid() {
        if (!isValid()) throw new IllegalStateException(
            id == 0 ? "DElement is undefined" : "DElement is stale (document was mutated)");
    }

    // ── Zero-allocation key comparison ───────────────────────────────────

    /**
     * Compares this element's raw source bytes against {@code expected} without
     * allocating a String.
     *
     * Fast path (STRING variant, no escapes): direct byte comparison via
     * {@link java.util.Arrays#equals(byte[],int,int,byte[],int,int)}.
     * The JIT typically auto-vectorizes this loop using SIMD instructions.
     *
     * Slow path (escaped string): decodes to String and compares.
     * In practice, JSON object keys are never escaped, so this path is cold.
     */
    public boolean rawMatchesBytes(byte[] expected) {
        long token = doc.getToken(id);
        if (!DToken.isText(token)) return false;
        long payload = DToken.payload(token);
        int offset = DToken.decodeOffset(payload);
        int length = DToken.decodeLength(payload);
        if (length != expected.length) return false;
        if (DToken.variant(token) == DTokenVariant.STRING) {
            // hot path: unescaped — compare source bytes directly
            return java.util.Arrays.equals(
                    doc.source(), offset, offset + length,
                    expected, 0, length);
        }
        // cold path: escaped key — decode first
        String decoded = doc.decodeString(id, token);
        return decoded != null
                && decoded.equals(new String(expected, java.nio.charset.StandardCharsets.UTF_8));
    }

    // ── Type queries ─────────────────────────────────────────────────────

    public boolean isNull() {
        checkValid();
        return DToken.isNull(doc.getToken(id));
    }

    public boolean isBoolean() {
        checkValid();
        return DToken.isLiteral(doc.getToken(id)) && !isNull();
    }

    public boolean isNumber() {
        checkValid();
        return DToken.isNumeric(doc.getToken(id));
    }

    public boolean isString() {
        checkValid();
        return DToken.isText(doc.getToken(id));
    }

    public boolean isArray() {
        checkValid();
        return DToken.isArray(doc.getToken(id));
    }

    public boolean isObject() {
        checkValid();
        return DToken.isMap(doc.getToken(id));
    }

    // ── Scalar accessors ─────────────────────────────────────────────────

    public boolean getBoolean() {
        checkValid();
        long t = doc.getToken(id);
        int v = DToken.variant(t);
        if (v == DTokenVariant.BOOLEAN_TRUE)  return true;
        if (v == DTokenVariant.BOOLEAN_FALSE) return false;
        throw new IllegalStateException("Element is not a boolean");
    }

    public String getString() {
        checkValid();
        long t = doc.getToken(id);
        if (DToken.isNull(t)) return null;
        if (!DToken.isText(t)) throw new IllegalStateException("Element is not a string or null");
        return doc.decodeString(id, t);
    }

    public int getInt() {
        checkValid();
        long t = doc.getToken(id);
        if (!DToken.isNumeric(t)) throw new IllegalStateException("Element is not a number");
        return (int) doc.decodeInteger(id, t);
    }

    public long getLong() {
        checkValid();
        long t = doc.getToken(id);
        if (!DToken.isNumeric(t)) throw new IllegalStateException("Element is not a number");
        return doc.decodeInteger(id, t);
    }

    public double getDouble() {
        checkValid();
        long t = doc.getToken(id);
        if (!DToken.isNumeric(t)) throw new IllegalStateException("Element is not a number");
        return doc.decodeFloat(id, t);
    }

    // ── Container views ──────────────────────────────────────────────────

    public DArray asArray() {
        checkValid();
        if (!DToken.isArray(doc.getToken(id)))
            throw new IllegalStateException("Element is not an array");
        return new DArray(doc, id, version);
    }

    public DObject asObject() {
        checkValid();
        if (!DToken.isMap(doc.getToken(id)))
            throw new IllegalStateException("Element is not an object");
        return new DObject(doc, id, version);
    }

    // ── Property access (shorthand) ───────────────────────────────────────

    /** Convenience: get a named property from an object element. */
    public DElement get(String key) {
        return asObject().get(key);
    }

    /** Convenience: get an indexed element from an array element. */
    public DElement get(int index) {
        return asArray().get(index);
    }

    // ── Identity ──────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DElement)) return false;
        DElement e = (DElement) o;
        return id == e.id && version == e.version && doc == e.doc;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * System.identityHashCode(doc) + id) + version;
    }

    @Override
    public String toString() {
        if (!isValid()) return "<invalid DElement>";
        long t = doc.getToken(id);
        if (DToken.isNull(t))      return "null";
        if (DToken.isTrue(t))      return "true";
        if (DToken.isFalse(t))     return "false";
        if (DToken.isText(t))      return '"' + doc.decodeString(id, t) + '"';
        if (DToken.isNumeric(t))   return String.valueOf(doc.decodeFloat(id, t));
        if (DToken.isArray(t))     return "[array:" + DToken.containerCount(t) + "]";
        if (DToken.isMap(t))       return "{object:" + DToken.containerCount(t) + "}";
        return "<token:" + Long.toHexString(t) + ">";
    }
}
