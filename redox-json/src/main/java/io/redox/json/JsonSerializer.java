package io.redox.json;

import io.redox.core.DElement;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Public API for REDox JSON serialization and deserialization.
 *
 * Dispatch order for deserialization:
 *   1. Single-pass StreamBindable (APT-generated, zero tape, fastest)
 *   2. Tape-based DoxConverter (APT-generated, raw-byte key compare)
 *   3. Reflective fallback (any POJO with no-arg constructor)
 */
public final class JsonSerializer {

    private static final ThreadLocal<JsonWriter> WRITER =
            ThreadLocal.withInitial(JsonWriter::new);

    private JsonSerializer() {}

    // ── Deserialize ───────────────────────────────────────────────────────

    public static <T> T deserialize(Class<T> type, byte[] utf8) {
        return deserialize(type, utf8, 0, utf8.length);
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialize(Class<T> type, byte[] utf8, int offset, int length) {
        DoxConverter<T> conv = DoxConverterRegistry.findOrLoad(type);
        if (conv instanceof StreamBindable) {
            // single-pass: no token tape, no wrapper objects
            return ((StreamBindable<T>) conv).bindDirect(utf8, offset, length);
        }
        if (conv != null) {
            // tape-based with raw-byte key comparison
            try (JsonDocument doc = JsonDocument.parse(utf8, offset, length)) {
                return conv.deserialize(doc.root());
            }
        }
        // reflective fallback
        try (JsonDocument doc = JsonDocument.parse(utf8, offset, length)) {
            return ReflectiveConverter.fromElement(doc.root(), type);
        }
    }

    public static <T> T deserialize(Class<T> type, String json) {
        byte[] utf8 = json.getBytes(StandardCharsets.UTF_8);
        return deserialize(type, utf8, 0, utf8.length);
    }

    public static <T> T deserialize(Class<T> type, InputStream stream) throws IOException {
        byte[] utf8 = stream.readAllBytes();
        return deserialize(type, utf8, 0, utf8.length);
    }

    /**
     * Bind a single DElement to a Java type (used as fallback from generated converters
     * for field types without a dedicated converter).
     */
    @SuppressWarnings("unchecked")
    public static <T> T bindElement(DElement element, Class<T> type) {
        DoxConverter<T> conv = DoxConverterRegistry.findOrLoad(type);
        if (conv != null) return conv.deserialize(element);
        return ReflectiveConverter.fromElement(element, type);
    }

    // ── Serialize ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public static byte[] serialize(Object obj) {
        if (obj == null) return "null".getBytes(StandardCharsets.UTF_8);
        DoxConverter<Object> conv =
                (DoxConverter<Object>) DoxConverterRegistry.findOrLoad(obj.getClass());
        if (conv != null) return conv.serialize(obj);
        return WRITER.get().objectToBytes(obj);
    }

    public static String serializeToString(Object obj) {
        return new String(serialize(obj), StandardCharsets.UTF_8);
    }

    // ── DOM access ────────────────────────────────────────────────────────

    public static JsonDocument parseDocument(byte[] utf8) { return JsonDocument.parse(utf8); }
    public static JsonDocument parseDocument(String json) { return JsonDocument.parse(json); }
}
