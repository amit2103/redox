package io.redox.json;

import io.redox.core.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Serialises a REDox token DOM back to JSON bytes.
 *
 * Key performance trick: string and number tokens store (offset, length) into
 * the original source buffer.  For unescaped strings and all numbers we bulk-copy
 * those bytes directly into the output rather than decoding and re-encoding.
 * This makes round-trip parse → write dramatically faster than libraries that
 * decode every value into a Java object first.
 */
public final class JsonWriter {

    // ── Output buffer ─────────────────────────────────────────────────────

    private byte[] buf;
    private int    pos;

    // ── Streaming state (used by generated converters) ────────────────────

    private int[] nestCount = new int[16]; // field/item count at each nesting depth
    private int   nestDepth = -1;

    public JsonWriter() {
        buf = new byte[4096];
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Serialize a DElement (and its subtree) to a UTF-8 JSON byte array. */
    public byte[] toBytes(DElement element) {
        pos = 0; nestDepth = -1;
        writeElement(element.document(), element.tokenId());
        return Arrays.copyOf(buf, pos);
    }

    /** Serialize a DElement to a JSON String. */
    public String toJsonString(DElement element) {
        return new String(toBytes(element), StandardCharsets.UTF_8);
    }

    /** Serialize a Java object to JSON bytes via reflection. */
    public byte[] objectToBytes(Object obj) {
        pos = 0; nestDepth = -1;
        writePojo(obj);
        return Arrays.copyOf(buf, pos);
    }

    /** Serialize a Java object to a JSON String. */
    public String objectToJsonString(Object obj) {
        return new String(objectToBytes(obj), StandardCharsets.UTF_8);
    }

    // ── DOM serialization ─────────────────────────────────────────────────

    private void writeElement(Document doc, int id) {
        long token = doc.getToken(id);

        if (DToken.isNull(token))  { writeRaw(NULL_BYTES);  return; }
        if (DToken.isTrue(token))  { writeRaw(TRUE_BYTES);  return; }
        if (DToken.isFalse(token)) { writeRaw(FALSE_BYTES); return; }

        if (DToken.isArray(token)) { writeArray(doc, id);  return; }
        if (DToken.isMap(token))   { writeObject(doc, id); return; }

        if (DToken.isText(token)) {
            writeJsonString(doc, id, token);
            return;
        }

        if (DToken.isNumeric(token)) {
            writeNumber(doc, id, token);
            return;
        }

        // fallback: unknown token kind → null
        writeRaw(NULL_BYTES);
    }

    private void writeArray(Document doc, int parentId) {
        int count = DToken.containerCount(doc.getToken(parentId));
        ensureCapacity(1);
        buf[pos++] = '[';
        int cur = parentId + 1;
        for (int i = 0; i < count; i++) {
            if (i > 0) { ensureCapacity(1); buf[pos++] = ','; }
            writeElement(doc, cur);
            cur = doc.nextToken(cur);
        }
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    private void writeObject(Document doc, int parentId) {
        int count = DToken.containerCount(doc.getToken(parentId));
        int pairs = count / 2;
        ensureCapacity(1);
        buf[pos++] = '{';
        int cur = parentId + 1;
        for (int i = 0; i < pairs; i++) {
            if (i > 0) { ensureCapacity(1); buf[pos++] = ','; }
            // key (must be a string token)
            writeJsonString(doc, cur, doc.getToken(cur));
            ensureCapacity(1);
            buf[pos++] = ':';
            cur = doc.nextToken(cur);
            // value
            writeElement(doc, cur);
            cur = doc.nextToken(cur);
        }
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    /** Write a string token — bulk-copies source bytes for unescaped strings. */
    private void writeJsonString(Document doc, int id, long token) {
        long payload = DToken.payload(token);
        int  offset  = DToken.decodeOffset(payload);
        int  length  = DToken.decodeLength(payload);
        int  variant = DToken.variant(token);

        ensureCapacity(length + 2);
        buf[pos++] = '"';

        if (variant == DTokenVariant.STRING) {
            // no escapes: bulk copy from source
            System.arraycopy(doc.source(), offset, buf, pos, length);
            pos += length;
        } else {
            // has escapes: write escaped form — re-escape from the decoded string
            // (simpler than re-scanning source bytes; acceptable for the escaped minority)
            String s = doc.decodeString(id, token);
            writeEscaped(s);
        }

        buf[pos++] = '"';
    }

    /** Write a number token — bulk-copies the raw ASCII digits from source. */
    private void writeNumber(Document doc, int id, long token) {
        int variant = DToken.variant(token);
        if (variant == DTokenVariant.INLINE_FLOAT) {
            byte[] d = Double.toString(DToken.inlineFloatValue(token))
                             .getBytes(StandardCharsets.US_ASCII);
            ensureCapacity(d.length);
            System.arraycopy(d, 0, buf, pos, d.length);
            pos += d.length;
            return;
        }
        // INTEGER or FLOAT: payload holds (length, offset) into source
        long payload = DToken.payload(token);
        int  offset  = DToken.decodeOffset(payload);
        int  length  = DToken.decodeLength(payload);
        ensureCapacity(length);
        System.arraycopy(doc.source(), offset, buf, pos, length);
        pos += length;
    }

    // ── POJO serialization ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    void writePojo(Object obj) {
        if (obj == null)                          { writeRaw(NULL_BYTES); return; }
        if (obj instanceof Boolean)               { writeRaw(((Boolean)obj) ? TRUE_BYTES : FALSE_BYTES); return; }
        if (obj instanceof Number)                { writeNumberValue((Number)obj); return; }
        if (obj instanceof String)                { writeRaw(new byte[]{'"'}); writeEscaped((String)obj); writeRaw(new byte[]{'"'}); return; }
        if (obj instanceof java.util.List)        { writeList((java.util.List<?>)obj); return; }
        if (obj instanceof Object[])              { writeObjectArray((Object[])obj); return; }
        if (obj.getClass().isArray())             { writePrimitiveArray(obj); return; }
        // POJO
        writePojoObject(obj);
    }

    private void writeNumberValue(Number n) {
        String s;
        if (n instanceof Double || n instanceof Float) {
            s = n.toString();
        } else {
            s = n.toString();
        }
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        ensureCapacity(b.length);
        System.arraycopy(b, 0, buf, pos, b.length);
        pos += b.length;
    }

    private void writeList(java.util.List<?> list) {
        ensureCapacity(1); buf[pos++] = '[';
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) { ensureCapacity(1); buf[pos++] = ','; }
            writePojo(list.get(i));
        }
        ensureCapacity(1); buf[pos++] = ']';
    }

    private void writeObjectArray(Object[] arr) {
        ensureCapacity(1); buf[pos++] = '[';
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) { ensureCapacity(1); buf[pos++] = ','; }
            writePojo(arr[i]);
        }
        ensureCapacity(1); buf[pos++] = ']';
    }

    private void writePrimitiveArray(Object arr) {
        ensureCapacity(1); buf[pos++] = '[';
        int len = java.lang.reflect.Array.getLength(arr);
        for (int i = 0; i < len; i++) {
            if (i > 0) { ensureCapacity(1); buf[pos++] = ','; }
            writePojo(java.lang.reflect.Array.get(arr, i));
        }
        ensureCapacity(1); buf[pos++] = ']';
    }

    private void writePojoObject(Object obj) {
        ClassDescriptor desc = ClassDescriptor.of(obj.getClass());
        ensureCapacity(1); buf[pos++] = '{';
        boolean first = true;
        for (ClassDescriptor.FieldInfo fi : desc.fields()) {
            Object val;
            try { val = fi.field().get(obj); } catch (IllegalAccessException e) { continue; }
            if (val == null && fi.skipNull()) continue;
            if (!first) { ensureCapacity(1); buf[pos++] = ','; }
            first = false;
            // key
            writeRaw(fi.encodedKeyBytes()); // pre-encoded `"name":`
            // value
            writePojo(val);
        }
        ensureCapacity(1); buf[pos++] = '}';
    }

    // ── Escape ────────────────────────────────────────────────────────────

    private void writeEscaped(String s) {
        // estimate: worst case every char is escaped as 6 bytes (\\uXXXX)
        ensureCapacity(s.length() * 6);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  buf[pos++]='\\'; buf[pos++]='"';  break;
                case '\\': buf[pos++]='\\'; buf[pos++]='\\'; break;
                case '\b': buf[pos++]='\\'; buf[pos++]='b';  break;
                case '\f': buf[pos++]='\\'; buf[pos++]='f';  break;
                case '\n': buf[pos++]='\\'; buf[pos++]='n';  break;
                case '\r': buf[pos++]='\\'; buf[pos++]='r';  break;
                case '\t': buf[pos++]='\\'; buf[pos++]='t';  break;
                default:
                    if (c < 0x20) {
                        buf[pos++]='\\'; buf[pos++]='u';
                        buf[pos++]='0';  buf[pos++]='0';
                        buf[pos++]=HEX[(c>>4)&0xF];
                        buf[pos++]=HEX[c&0xF];
                    } else if (c < 0x80) {
                        buf[pos++] = (byte) c;
                    } else {
                        // multi-byte UTF-8
                        byte[] utf8 = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                        ensureCapacity(utf8.length + s.length() * 2);
                        System.arraycopy(utf8, 0, buf, pos, utf8.length);
                        pos += utf8.length;
                    }
            }
        }
    }

    // ── Buffer management ─────────────────────────────────────────────────

    private void ensureCapacity(int need) {
        if (pos + need > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + need + 64));
        }
    }

    private void writeRaw(byte[] bytes) {
        ensureCapacity(bytes.length);
        System.arraycopy(bytes, 0, buf, pos, bytes.length);
        pos += bytes.length;
    }

    // ── Streaming API (for APT-generated converters) ──────────────────────

    /** Begin a JSON object. Must be paired with {@link #endObject()}. */
    public void beginObject() {
        ensureCapacity(1);
        buf[pos++] = '{';
        if (nestDepth + 1 >= nestCount.length)
            nestCount = Arrays.copyOf(nestCount, nestCount.length * 2);
        nestCount[++nestDepth] = 0;
    }

    /**
     * Write a JSON object key including the trailing colon.
     * Automatically inserts a comma separator when this is not the first field.
     */
    public void writeKey(String key) {
        if (nestCount[nestDepth]++ > 0) { ensureCapacity(1); buf[pos++] = ','; }
        ensureCapacity(key.length() + 3);
        buf[pos++] = '"';
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);
        ensureCapacity(kb.length);
        System.arraycopy(kb, 0, buf, pos, kb.length);
        pos += kb.length;
        buf[pos++] = '"';
        buf[pos++] = ':';
    }

    /** End a JSON object. */
    public void endObject() {
        nestDepth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    /** Begin a JSON array. Must be paired with {@link #endArray()}. */
    public void beginArray() {
        ensureCapacity(1);
        buf[pos++] = '[';
        if (nestDepth + 1 >= nestCount.length)
            nestCount = Arrays.copyOf(nestCount, nestCount.length * 2);
        nestCount[++nestDepth] = 0;
    }

    /**
     * Must be called before writing each array element.
     * Inserts a comma separator for all elements after the first.
     */
    public void nextArrayItem() {
        if (nestCount[nestDepth]++ > 0) { ensureCapacity(1); buf[pos++] = ','; }
    }

    /** End a JSON array. */
    public void endArray() {
        nestDepth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    public void writeNull()             { writeRaw(NULL_BYTES); }
    public void writeBoolean(boolean v) { writeRaw(v ? TRUE_BYTES : FALSE_BYTES); }

    public void writeInt(int v) {
        byte[] b = Integer.toString(v).getBytes(StandardCharsets.US_ASCII);
        ensureCapacity(b.length); System.arraycopy(b, 0, buf, pos, b.length); pos += b.length;
    }

    public void writeLong(long v) {
        byte[] b = Long.toString(v).getBytes(StandardCharsets.US_ASCII);
        ensureCapacity(b.length); System.arraycopy(b, 0, buf, pos, b.length); pos += b.length;
    }

    public void writeDouble(double v) {
        byte[] b = Double.toString(v).getBytes(StandardCharsets.US_ASCII);
        ensureCapacity(b.length); System.arraycopy(b, 0, buf, pos, b.length); pos += b.length;
    }

    public void writeFloat(float v) {
        byte[] b = Float.toString(v).getBytes(StandardCharsets.US_ASCII);
        ensureCapacity(b.length); System.arraycopy(b, 0, buf, pos, b.length); pos += b.length;
    }

    public void writeString(String s) {
        ensureCapacity(s.length() * 6 + 2);
        buf[pos++] = '"'; writeEscaped(s); buf[pos++] = '"';
    }

    public void writeStringOrNull(String s) {
        if (s == null) writeNull(); else writeString(s);
    }

    /**
     * Called by generated {@code serialize()} methods to extract the final byte array
     * and reset internal state for reuse.
     */
    public byte[] finishBytes() {
        byte[] result = Arrays.copyOf(buf, pos);
        pos = 0;
        nestDepth = -1;
        return result;
    }

    // ── Constants ─────────────────────────────────────────────────────────

    private static final byte[] NULL_BYTES  = "null".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TRUE_BYTES  = "true".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FALSE_BYTES = "false".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
}
