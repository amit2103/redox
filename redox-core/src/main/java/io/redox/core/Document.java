package io.redox.core;

import java.util.Arrays;

/**
 * Manages the token tape: a long[] that holds every parsed token.
 *
 * Token 0 is reserved (NONE sentinel). The root token is always at index 1.
 * Subclasses (JsonDocument, CborDocument…) populate the tape via the
 * protected alloc/link helpers.
 */
public abstract class Document implements AutoCloseable {

    // ── Token tape ───────────────────────────────────────────────────────

    protected long[] tokens;
    protected int    tokenCount;  // next free slot index (tokens[0] reserved)

    /**
     * Parallel to the token tape: stores the "link id" for container tokens
     * (index of the last child, used by the linked-list relink logic) and
     * the id of the matching close-bracket token for quick skip-ahead.
     *
     * extends[2*i]   = linkId of token i  (last-child pointer for containers)
     * extends[2*i+1] = skipId of token i  (index of token after this container)
     */
    protected int[] extends_;
    protected int   version;     // incremented on every mutation

    // ── Source buffer ─────────────────────────────────────────────────────

    /** The raw UTF-8 bytes of the source document (or null for synthetic docs). */
    protected byte[] source;
    protected int    sourceLen;

    // ── Root ─────────────────────────────────────────────────────────────

    protected int rootId = 1;

    // ── Constructor ──────────────────────────────────────────────────────

    protected Document() {
        tokens    = new long[64];
        extends_  = new int[128];  // 2 ints per token
        tokenCount = 1;            // slot 0 is reserved NONE
    }

    // ── Token allocation ─────────────────────────────────────────────────

    /** Allocates the next token slot, grows the tape if needed, returns its id. */
    protected final int allocToken(long token) {
        if (tokenCount == tokens.length) grow();
        int id = tokenCount++;
        tokens[id] = token;
        return id;
    }

    /** Overwrites an already-allocated token (e.g., back-patching a container). */
    protected final void setToken(int id, long token) {
        tokens[id] = token;
        version++;
    }

    public final long getToken(int id) {
        return tokens[id];
    }

    /** Returns the number of allocated tokens (excluding slot 0). */
    public final int tokenCount() { return tokenCount - 1; }

    public final int version()    { return version; }
    public final int rootId()     { return rootId; }

    // ── Container helpers ────────────────────────────────────────────────

    /**
     * Returns the stored linkId for a container token.
     * linkId is the id of the last-child token (used for O(1) append).
     */
    protected final int getLinkId(int id) {
        return extends_[id * 2];
    }

    protected final void setLinkId(int id, int linkId) {
        ensureExtends(id);
        extends_[id * 2] = linkId;
    }

    /**
     * The skip-id is the index of the first token after this container,
     * enabling O(1) container traversal skip.
     */
    protected final int getSkipId(int id) {
        return extends_[id * 2 + 1];
    }

    protected final void setSkipId(int id, int skipId) {
        ensureExtends(id);
        extends_[id * 2 + 1] = skipId;
    }

    /**
     * Increments the element count packed inside a container token.
     * Container token layout: EXTENSION_BIT | kind | linkId | count
     */
    protected final void incContainerCount(int id) {
        long t = tokens[id];
        int count = DToken.containerCount(t) + 1;
        // rebuild token preserving kind and linkId, updating count
        long kind    = t & 0x7000_0000_0000_0000L;
        long linkBits= t & 0x0FFF_FFFF_0000_0000L;
        tokens[id] = DToken.EXTENSION_BIT | kind | linkBits | (count & 0x3FFF_FFFFL);
    }

    // ── Navigation ───────────────────────────────────────────────────────

    /**
     * Returns the id of the token immediately following token[id].
     * For containers, jumps past all their children using skipId.
     * For scalars, returns id+1.
     */
    public final int nextToken(int id) {
        long t = tokens[id];
        if (DToken.isContainer(t)) {
            int skip = getSkipId(id);
            return skip > 0 ? skip : id + 1;
        }
        return id + 1;
    }

    // ── Source access ────────────────────────────────────────────────────

    public final byte[] source()    { return source; }
    public final int    sourceLen() { return sourceLen; }

    /**
     * Extracts a raw (unescaped) UTF-8 string slice from the source buffer.
     * Safe to call only when the token variant is STRING (no escapes needed).
     */
    public String rawString(int offset, int length) {
        return new String(source, offset, length, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── Element factory ──────────────────────────────────────────────────

    /** Creates a DElement handle for the given token id, version-stamped. */
    public final DElement elementAt(int id) {
        return new DElement(this, id, version);
    }

    // ── Abstract decode hooks (format-specific) ───────────────────────────

    /** Decode a text token to a Java String (handles escape sequences). */
    public abstract String decodeString(int id, long token);

    /** Decode a numeric token to a long (truncates floats). */
    public abstract long decodeInteger(int id, long token);

    /** Decode a numeric token to a double. */
    public abstract double decodeFloat(int id, long token);

    // ── Lifecycle ─────────────────────────────────────────────────────────

    protected void reset() {
        Arrays.fill(tokens, 0, tokenCount, 0L);
        Arrays.fill(extends_, 0, Math.min(extends_.length, tokenCount * 2), 0);
        tokenCount = 1;
        source    = null;
        sourceLen = 0;
        rootId    = 1;
        version++;
    }

    @Override
    public void close() {
        reset();
    }

    // ── Private ──────────────────────────────────────────────────────────

    private void grow() {
        int newLen = tokens.length * 2;
        tokens   = Arrays.copyOf(tokens,   newLen);
        extends_ = Arrays.copyOf(extends_, newLen * 2);
    }

    private void ensureExtends(int id) {
        if (id * 2 + 1 >= extends_.length) {
            extends_ = Arrays.copyOf(extends_, Math.max(extends_.length * 2, id * 2 + 2));
        }
    }
}
