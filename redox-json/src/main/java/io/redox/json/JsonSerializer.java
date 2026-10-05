package io.redox.json;

import io.redox.core.DElement;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Public API for REDox JSON serialization and deserialization.
 *
 * Dispatch order for deserialization:
 *   1. APT-generated DoxConverter (zero reflection, fastest)
 *   2. Reflective fallback (works for any POJO with no-arg constructor)
 *
 * <pre>
 *   Player p = JsonSerializer.deserialize(Player.class, jsonBytes);
 *   byte[] bytes = JsonSerializer.serialize(p);
 * </pre>
 */
public final class JsonSerializer {

    private static final ThreadLocal<JsonWriter> WRITER =
            ThreadLocal.withInitial(JsonWriter::new);

    private JsonSerializer() {}

    // ── Deserialize ───────────────────────────────────────────────────────

    public static <T> T deserialize(Class<T> type, byte[] utf8) {
        try (JsonDocument doc = JsonDocument.parse(utf8)) {
            return bindElement(doc.root(), type);
        }
    }

    public static <T> T deserialize(Class<T> type, String json) {
        return deserialize(type, json.getBytes(StandardCharsets.UTF_8));
    }

    public static <T> T deserialize(Class<T> type, InputStream stream) throws IOException {
        return deserialize(type, stream.readAllBytes());
    }

    /**
     * Bind a single DElement to a Java type.
     * Used as a fallback from generated converters for field types that have no converter.
     */
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
