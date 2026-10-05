package io.redox.json;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry mapping Class → DoxConverter.
 *
 * APT-generated converters self-register in their static initializers.
 * {@link #findOrLoad} uses a naming convention to trigger class loading on first access.
 *
 * Naming convention (must match the APT processor):
 *   Binary class name with '$' replaced by '_', plus "DoxConverter" suffix.
 *   e.g.  io.example.Outer$Inner  →  io.example.Outer_InnerDoxConverter
 */
public final class DoxConverterRegistry {

    private static final ConcurrentHashMap<Class<?>, Object> CACHE = new ConcurrentHashMap<>();
    private static final Object ABSENT = new Object(); // sentinel: no converter

    private DoxConverterRegistry() {}

    public static <T> void register(Class<T> type, DoxConverter<T> converter) {
        CACHE.put(type, converter);
    }

    /**
     * Returns the converter for {@code type}, or {@code null} if none exists.
     * Triggers class loading on first call so the converter's static init can fire.
     */
    @SuppressWarnings("unchecked")
    public static <T> DoxConverter<T> findOrLoad(Class<T> type) {
        Object cached = CACHE.get(type);
        if (cached != null) {
            return cached == ABSENT ? null : (DoxConverter<T>) cached;
        }
        // First access: try to load the generated converter class
        String converterFqn = converterClassNameFor(type);
        try {
            Class.forName(converterFqn, true, type.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            CACHE.putIfAbsent(type, ABSENT);
        }
        cached = CACHE.getOrDefault(type, ABSENT);
        return cached == ABSENT ? null : (DoxConverter<T>) cached;
    }

    /** Derives the fully-qualified converter class name from a type. */
    static String converterClassNameFor(Class<?> type) {
        String binaryName = type.getName(); // uses $ for nested classes
        int lastDot = binaryName.lastIndexOf('.');
        String pkg   = (lastDot >= 0) ? binaryName.substring(0, lastDot + 1) : "";
        String local = (lastDot >= 0) ? binaryName.substring(lastDot + 1)    : binaryName;
        return pkg + local.replace('$', '_') + "DoxConverter";
    }
}
