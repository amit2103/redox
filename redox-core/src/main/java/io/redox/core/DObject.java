package io.redox.core;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** Read-only view over a map/object token: alternating key-value pairs. */
public final class DObject implements Iterable<DProperty> {

    private final Document doc;
    private final int      id;
    private final int      version;

    DObject(Document doc, int id, int version) {
        this.doc     = doc;
        this.id      = id;
        this.version = version;
    }

    /** Number of key-value pairs. Container count stores pairs*2, so halve. */
    public int size() {
        return DToken.containerCount(doc.getToken(id)) / 2;
    }

    /**
     * O(n) key lookup — walks key tokens comparing against the source bytes.
     * Returns UNDEFINED if not found.
     */
    public DElement get(String key) {
        byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return get(keyBytes);
    }

    /** Zero-allocation overload: compare raw bytes directly against the token source. */
    public DElement get(byte[] keyBytes) {
        int cur   = id + 1;
        int pairs = DToken.containerCount(doc.getToken(id)) / 2;
        for (int i = 0; i < pairs; i++) {
            long keyToken = doc.getToken(cur);
            if (DToken.isText(keyToken)) {
                long payload = DToken.payload(keyToken);
                int  offset  = DToken.decodeOffset(payload);
                int  length  = DToken.decodeLength(payload);
                if (length == keyBytes.length
                        && DToken.variant(keyToken) == DTokenVariant.STRING
                        && java.util.Arrays.equals(
                                doc.source(), offset, offset + length,
                                keyBytes, 0, length)) {
                    return new DElement(doc, doc.nextToken(cur), version);
                }
                // fallback for escaped key tokens
                if (DToken.variant(keyToken) != DTokenVariant.STRING) {
                    String k = doc.decodeString(cur, keyToken);
                    String expected = new String(keyBytes, java.nio.charset.StandardCharsets.UTF_8);
                    if (expected.equals(k)) {
                        return new DElement(doc, doc.nextToken(cur), version);
                    }
                }
            }
            cur = doc.nextToken(doc.nextToken(cur));
        }
        return DElement.UNDEFINED;
    }

    public boolean containsKey(String key) {
        return get(key).isValid();
    }

    @Override
    public Iterator<DProperty> iterator() {
        return new Iter();
    }

    private final class Iter implements Iterator<DProperty> {
        private int cur    = id + 1;
        private int remain = DToken.containerCount(doc.getToken(id)) / 2;

        @Override public boolean hasNext() { return remain > 0; }

        @Override public DProperty next() {
            if (remain-- == 0) throw new NoSuchElementException();
            DElement key   = new DElement(doc, cur, version);
            cur = doc.nextToken(cur);
            DElement value = new DElement(doc, cur, version);
            cur = doc.nextToken(cur);
            return new DProperty(key, value);
        }
    }
}
