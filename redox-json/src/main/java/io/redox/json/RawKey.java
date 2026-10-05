package io.redox.json;

import java.nio.charset.StandardCharsets;

/**
 * Utility for pre-encoding JSON key strings as UTF-8 byte arrays.
 *
 * Used exclusively by APT-generated converters. Each converter has one
 * {@code static final byte[] _K_xxx} field per JSON key, initialized once
 * at class load time via {@link #encode(String)}.
 *
 * At comparison time, {@link io.redox.core.DElement#rawMatchesBytes(byte[])}
 * compares source bytes directly against these pre-encoded arrays — no String
 * allocation on the hot path.
 */
public final class RawKey {
    private RawKey() {}

    public static byte[] encode(String key) {
        return key.getBytes(StandardCharsets.UTF_8);
    }
}
