package io.redox.json;

/**
 * Implemented by APT-generated converters that support single-pass binding.
 *
 * Bypasses the token tape entirely: parses source bytes and writes POJO fields
 * in one forward pass, allocating only the POJO objects themselves (no DElement,
 * DProperty, DArray, DObject, or token tape).
 */
public interface StreamBindable<T> {

    /**
     * Parse {@code src[offset..offset+length]} and bind directly to T.
     * Entry point — creates the parser and positions it at the root value.
     */
    T bindDirect(byte[] src, int offset, int length);

    /**
     * Bind from a parser that is already positioned at the start of this value
     * ({@code p.currentToken()} == the first token of the value to bind, e.g.
     * {@code START_OBJECT} for a POJO or {@code VALUE_NULL} for null).
     */
    T bindFromParser(JsonStreamParser p);
}
